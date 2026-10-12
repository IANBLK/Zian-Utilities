package com.zianblk.zianutilities.neoforge.equipment.client;
import com.zianblk.zianutilities.neoforge.equipment.ImportedEquipment;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
/** Original rotated geometry and per-face UVs, attached to Minecraft's animated armor bones. */
@EventBusSubscriber(modid="zianutilities",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ImportedArmorRenderer {
 private static final Map<String,Mesh> CACHE=new HashMap<>();
 private record Face(float[][] local,float[][] worn,float[][] world,float[] normal){}
 private record Bone(EquipmentSlot slot,String part,List<Face> faces){}
 private record Mesh(ResourceLocation texture,List<Bone> bones){}
 @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)manager->CACHE.clear());}
 @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event){
  for(var item:ImportedEquipment.ITEMS.values())if(item.get() instanceof ImportedEquipment.NamedArmor armor && armor.mesh){
   event.registerItem(new IClientItemExtensions(){
    private final Map<EquipmentSlot,ArmorModel> models=new EnumMap<>(EquipmentSlot.class);private ItemRenderer renderer;
    @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity,ItemStack stack,EquipmentSlot slot,HumanoidModel<?> original){return models.computeIfAbsent(slot,key->new ArmorModel(armor.set,key));}
    @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new ItemRenderer();return renderer;}
   },armor);
  }
 }
 private static float[][] vertices(JsonArray array){float[][] result=new float[4][5];if(array.size()!=4)throw new IllegalArgumentException("Four vertices required");for(int i=0;i<4;i++){var row=array.get(i).getAsJsonArray();if(row.size()!=5)throw new IllegalArgumentException("Invalid mesh vertex");for(int j=0;j<5;j++){result[i][j]=row.get(j).getAsFloat();if(!Float.isFinite(result[i][j]))throw new IllegalArgumentException("Non-finite vertex");}}return result;}
 private static Mesh mesh(String set){return CACHE.computeIfAbsent(set,key->{
  try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath("zianutilities","equipment/"+key+".json"))){
   var root=JsonParser.parseReader(reader).getAsJsonObject();if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Mesh schema");var bones=new ArrayList<Bone>();
   for(var raw:root.getAsJsonArray("bones")){var b=raw.getAsJsonObject();var faces=new ArrayList<Face>();for(var entry:b.getAsJsonArray("faces")){var f=entry.getAsJsonObject();var n=f.getAsJsonArray("normal");var local=vertices(f.getAsJsonArray("vertices"));var worn=f.has("wornVertices")?vertices(f.getAsJsonArray("wornVertices")):local;faces.add(new Face(local,worn,vertices(f.getAsJsonArray("world")),new float[]{n.get(0).getAsFloat(),n.get(1).getAsFloat(),n.get(2).getAsFloat()}));}bones.add(new Bone(EquipmentSlot.valueOf(b.get("slot").getAsString()),b.get("part").getAsString(),List.copyOf(faces)));}
   return new Mesh(ResourceLocation.parse(root.get("texture").getAsString()),List.copyOf(bones));
  }catch(Exception error){throw new IllegalStateException("Invalid imported armor mesh: "+key,error);}
 });}
 private static void draw(PoseStack pose,VertexConsumer buffer,float[][] vertices,float[] normal,int light,int overlay,int color,float divisor){for(var v:vertices)buffer.addVertex(pose.last(),v[0]/divisor,v[1]/divisor,v[2]/divisor).setColor(color).setUv(v[3],v[4]).setOverlay(overlay).setLight(light).setNormal(pose.last(),normal[0],normal[1],normal[2]);}
 private static final class ArmorModel extends HumanoidModel<LivingEntity> {
  private final String set;private final EquipmentSlot slot;
  ArmorModel(String set,EquipmentSlot slot){super(LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),128,128).bakeRoot());this.set=set;this.slot=slot;}
  private ModelPart part(String name){return switch(name){case "head"->head;case "body"->body;case "right_arm"->rightArm;case "left_arm"->leftArm;case "right_leg"->rightLeg;case "left_leg"->leftLeg;default->throw new IllegalArgumentException("Unknown bone");};}
  @Override public void renderToBuffer(PoseStack pose,VertexConsumer buffer,int light,int overlay,int color){
   pose.pushPose();try{
    if(young){float scale=slot==EquipmentSlot.HEAD?.75f:.5f;pose.scale(scale,scale,scale);pose.translate(0,slot==EquipmentSlot.HEAD?1:1.5,0);}
    for(var bone:mesh(set).bones())if(bone.slot()==slot){var part=part(bone.part());if(!part.visible)continue;pose.pushPose();try{part.translateAndRotate(pose);for(var face:bone.faces())draw(pose,buffer,face.worn(),face.normal(),light,overlay,color,16);}finally{pose.popPose();}}
   }finally{pose.popPose();}
  }
 }
 private static final class ItemRenderer extends BlockEntityWithoutLevelRenderer {
  ItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
  @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
   if(!(stack.getItem() instanceof ImportedEquipment.NamedArmor armor))return;var mesh=mesh(armor.set);var slot=armor.getEquipmentSlot();float[] low={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY},high={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
   for(var bone:mesh.bones())if(bone.slot()==slot)for(var face:bone.faces())for(var v:face.world())for(int i=0;i<3;i++){low[i]=Math.min(low[i],v[i]);high[i]=Math.max(high[i],v[i]);}
   float diameter=0;for(var bone:mesh.bones())if(bone.slot()==slot)for(var face:bone.faces())for(var v:face.world()){float radiusSquared=0;for(int i=0;i<3;i++){float distance=v[i]-(low[i]+high[i])/2;radiusSquared+=distance*distance;}diameter=Math.max(diameter,2*(float)Math.sqrt(radiusSquared));}if(!Float.isFinite(diameter)||diameter<=0)return;
   var buffer=net.minecraft.client.renderer.entity.ItemRenderer.getFoilBufferDirect(buffers,RenderType.entityCutoutNoCull(mesh.texture()),true,stack.hasFoil());
   pose.pushPose();try{pose.translate(.5,.5,.5);float scale=.8f/diameter;pose.scale(-scale,-scale,scale);pose.translate(-(low[0]+high[0])/2,-(low[1]+high[1])/2,-(low[2]+high[2])/2);for(var bone:mesh.bones())if(bone.slot()==slot)for(var face:bone.faces())draw(pose,buffer,face.world(),face.normal(),light,overlay,0xFFFFFFFF,1);}finally{pose.popPose();}
  }
 }
}
