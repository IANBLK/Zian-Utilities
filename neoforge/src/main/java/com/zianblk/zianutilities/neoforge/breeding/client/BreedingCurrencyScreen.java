package com.zianblk.zianutilities.neoforge.breeding.client;
import com.zianblk.zianutilities.neoforge.breeding.BreedingNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Consumer;
public final class BreedingCurrencyScreen extends com.zianblk.zianutilities.neoforge.client.ResponsiveScreen {
 private int page;
 private final Screen parent;private final List<BreedingNetwork.Currency> currencies;private final Consumer<String> accept;
 public BreedingCurrencyScreen(Screen parent,List<BreedingNetwork.Currency> currencies,Consumer<String> accept){super(Component.literal("Elegir moneda o ticket"));this.parent=parent;this.currencies=List.copyOf(currencies);this.accept=accept;}
 @Override protected void init(){fitViewport(560,380);int x=(width-500)/2,y=(height-340)/2;int pages=Math.max(1,(currencies.size()+7)/8);page=Math.min(page,pages-1);for(int i=0;i<Math.min(8,currencies.size()-page*8);i++){var currency=currencies.get(page*8+i);var id=ResourceLocation.parse(currency.id());var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);String name=item.getDescription().getString();String label=name+" · "+currency.minutes()+" min · saldo "+(currency.balance()<0?"no disponible":currency.balance());button(x+12,y+48+i*30,476,label,()->{accept.accept(currency.id());minecraft.setScreen(parent);});}if(page>0)button(x+12,y+310,130,"Anterior",()->{page--;rebuildWidgets();});button(x+180,y+310,140,"Volver",()->minecraft.setScreen(parent));if(page+1<pages)button(x+358,y+310,130,"Siguiente",()->{page++;rebuildWidgets();});}
 private void button(int x,int y,int w,String name,Runnable action){addRenderableWidget(new Button(x,y,w,24,Component.literal(name),b->action.run(),message->message.get()){@Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),0xFFF2C14E);g.fill(getX()+1,getY()+1,getX()+getWidth()-1,getY()+getHeight()-1,isHoveredOrFocused()?0xFF38332A:0xFF242424);g.drawCenteredString(font,getMessage(),getX()+getWidth()/2,getY()+8,0xFFF7E2AC);}});}
 @Override protected void renderCanvas(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xF0181818);g.drawCenteredString(font,title,width/2,(height-340)/2+14,0xFFF2C14E);renderWidgets(g,mx,my,partial);}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
