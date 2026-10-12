package com.zianblk.zianutilities.neoforge.riding;
import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class RidingNetwork {
 private RidingNetwork(){}
 public record Snapshot(boolean enabled,int speed,int stamina)implements CustomPacketPayload{
  public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ZianUtilitiesMod.MOD_ID,"riding_policy"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,v)->{b.writeBoolean(v.enabled);b.writeVarInt(v.speed);b.writeVarInt(v.stamina);},b->new Snapshot(b.readBoolean(),b.readVarInt(),b.readVarInt()));
  public Snapshot{new RidingPolicy.Settings(enabled,speed,stamina);}
  @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 public static void register(RegisterPayloadHandlersEvent event){event.registrar("riding-policy-1").playToClient(Snapshot.TYPE,Snapshot.CODEC,(v,c)->c.enqueueWork(()->RidingPolicy.client(new RidingPolicy.Settings(v.enabled(),v.speed(),v.stamina()))));}
 public static void send(ServerPlayer player){if(player.connection!=null&&NetworkRegistry.hasChannel(player.connection,Snapshot.TYPE.id())){var s=RidingPolicy.server();PacketDistributor.sendToPlayer(player,new Snapshot(s.enabled(),s.speedPercent(),s.staminaPercent()));}}
}
