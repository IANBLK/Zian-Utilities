package com.zianblk.zianutilities.neoforge.breeding.genetics
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.*
import com.cobblemon.mod.common.pokemon.properties.*
import com.zianblk.zianutilities.core.breeding.BreedingJob
import com.cobblemon.mod.common.api.pokemon.feature.FlagSpeciesFeature
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature
import com.cobblemon.mod.common.util.DataKeys
import java.util.UUID
import java.util.function.Predicate
import kotlin.random.Random
import net.neoforged.fml.ModList
/** Adapted hatch finalization from Cobbreeding 2.4.0; full MIT notice bundled. */
object FrozenOffspring {
 @JvmStatic fun generate(parents:List<Pokemon>,seed:Long,allowed:Predicate<FormData>,alphaChance:Double):BreedingJob.Child {
  require(alphaChance in 0.0..1.0)
  val random=Random(seed)
  val properties=BreedingUtilities.generate(parents,random,allowed) ?: error("Padres incompatibles o generaciÃ³n del hijo bloqueada")
  val species=PokemonSpecies.getByName(properties.species!!) ?: error("Especie no disponible")
  val form=species.forms.find {it.formOnlyShowdownId()==properties.form} ?: species.standardForm
  if(properties.gender==null)properties.gender=when {form.maleRatio<0 -> Gender.GENDERLESS;random.nextFloat()<form.maleRatio -> Gender.MALE;else -> Gender.FEMALE}
  form.aspects.forEach { aspect -> properties.customProperties.add(FlagSpeciesFeature(aspect,true));if(aspect=="whitestriped")properties.customProperties.add(StringSpeciesFeature("fish_stripes","white"));properties.customProperties.add(StringSpeciesFeature("region_bias",aspect.split("-").last())) }
  properties.level=1
  val alpha=properties.isAlpha ?: (random.nextDouble()<alphaChance)
  properties.isAlpha=null
  val pokemon=properties.create();pokemon.setFriendship(120)
  properties.isAlpha=alpha;properties.apply(pokemon)
  if(ModList.get().isLoaded("cobblemonsizevariation")){
   val cls=Class.forName("dev.cudzer.cobblemonsizevariation.CobblemonSizeVariation")
   val sizer=cls.getField("SIZER").get(null)
   val api=Class.forName("dev.cudzer.cobblemonsizevariation.sizing.algorithms.ISizer")
   pokemon.scaleModifier=(api.getMethod("getSize").invoke(sizer) as Number).toFloat()
  }
  if(pokemon.species.showdownId() in setOf("wooloo","mareep")){val feature=pokemon.features.filterIsInstance<FlagSpeciesFeature>().find{it.name==DataKeys.HAS_BEEN_SHEARED};if(feature!=null){feature.enabled=true;pokemon.markFeatureDirty(feature);pokemon.updateAspects()}}
  val uuid=UUID.randomUUID();pokemon.uuid=uuid
  // Freeze even API-chosen defaults (for example an empty Ferroseed ability).
  properties.ability=pokemon.ability.name;properties.nature=pokemon.nature.name.toString();properties.gender=pokemon.gender;properties.ivs=pokemon.ivs;properties.pokeball=pokemon.caughtBall.name.toString();properties.shiny=pokemon.shiny;properties.form=pokemon.form.formOnlyShowdownId()
  val stats=listOf(Stats.HP,Stats.ATTACK,Stats.DEFENCE,Stats.SPECIAL_ATTACK,Stats.SPECIAL_DEFENCE,Stats.SPEED)
  return BreedingJob.Child(1,uuid,pokemon.species.resourceIdentifier.toString(),pokemon.form.formOnlyShowdownId(),properties.asString(),stats.map{pokemon.ivs[it]?:0},pokemon.nature.name.toString(),pokemon.ability.name,pokemon.gender.name,pokemon.caughtBall.name.toString(),pokemon.shiny,pokemon.isAlpha,pokemon.scaleModifier)
 }
 @JvmStatic fun matches(pokemon:Pokemon,child:BreedingJob.Child):Boolean {
  val stats=listOf(Stats.HP,Stats.ATTACK,Stats.DEFENCE,Stats.SPECIAL_ATTACK,Stats.SPECIAL_DEFENCE,Stats.SPEED)
  val requestedMoves=com.cobblemon.mod.common.api.pokemon.PokemonProperties.parse(child.properties()).moves.orEmpty()
  val learnedMoves=pokemon.allAccessibleMoves.map{it.name}.toSet()+pokemon.moveSet.getMoves().map{it.template.name}.toSet()
  return requestedMoves.all{it in learnedMoves} && pokemon.level==1 && pokemon.uuid==child.uuid() && pokemon.species.resourceIdentifier.toString()==child.species() && pokemon.form.formOnlyShowdownId()==child.form() && pokemon.gender.name==child.gender() && pokemon.shiny==child.shiny() && pokemon.isAlpha==child.alpha() && pokemon.nature.name.toString()==child.nature() && pokemon.ability.name==child.ability() && pokemon.caughtBall.name.toString()==child.ball() && stats.map{pokemon.ivs[it]?:0}==child.ivs() && kotlin.math.abs(pokemon.scaleModifier-child.scale())<0.00001
 }
 @JvmStatic fun materialize(child:BreedingJob.Child):Pokemon {
  val properties=com.cobblemon.mod.common.api.pokemon.PokemonProperties.parse(child.properties())
  val alpha=properties.isAlpha;properties.isAlpha=null;val pokemon=properties.create();properties.isAlpha=alpha;properties.apply(pokemon)
  pokemon.uuid=child.uuid();pokemon.scaleModifier=child.scale();pokemon.setFriendship(120)
  if(pokemon.species.showdownId() in setOf("wooloo","mareep")){val f=pokemon.features.filterIsInstance<FlagSpeciesFeature>().find{it.name==DataKeys.HAS_BEEN_SHEARED};if(f!=null){f.enabled=true;pokemon.markFeatureDirty(f);pokemon.updateAspects()}}
  if(!matches(pokemon,child))error("El hijo no coincide con el registro reservado")
  return pokemon
 }
}
