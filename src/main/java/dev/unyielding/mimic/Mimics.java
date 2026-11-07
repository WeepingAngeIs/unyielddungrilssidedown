package dev.unyielding.mimic;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Pre;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class Mimics {
   static final String TAG = "cu_mimic";
   static final String FLOOR = "cu_floor_";
   static final String TOP = "cu_top";
   static final String NAME = "Mimic";
   private static final String K_FAKE = "cu_mimic_fake";
   private static final String K_SRC = "cu_mimic_src";
   private static final String K_NAME = "cu_mimic_name";
   private static final String K_TEX = "cu_mimic_tex";
   private static final String K_SIG = "cu_mimic_sig";
   private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{
      EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   static final Map<Integer, Mimics.Disguise> BY_ID = new ConcurrentHashMap<>();
   private static final List<Runnable> END_OF_TICK = new ArrayList<>();
   private static int ticks;

   private Mimics() {
   }

   static void register(IEventBus bus) {
      bus.addListener(ServerStartedEvent.class, e -> MimicAwards.ensureObjective(e.getServer()));
      bus.addListener(ServerStoppedEvent.class, e -> {
         BY_ID.clear();
         END_OF_TICK.clear();
         MimicFloors.clear();
      });
      bus.addListener(PlayerLoggedInEvent.class, e -> {
         if (e.getEntity() instanceof ServerPlayer sp) {
            MimicNet.install(sp);
         }
      });
      bus.addListener(EventPriority.LOW, EntityJoinLevelEvent.class, Mimics::onJoin);
      bus.addListener(EntityLeaveLevelEvent.class, e -> {
         if (!e.getLevel().isClientSide()) {
            BY_ID.remove(e.getEntity().getId());
         }
      });
      bus.addListener(EventPriority.HIGH, LivingDeathEvent.class, Mimics::onDeath);
      bus.addListener(EventPriority.LOWEST, LivingDropsEvent.class, Mimics::onDrops);
      bus.addListener(Post.class, Mimics::onHurt);
      bus.addListener(Pre.class, e -> {
         if (isMimic(e.getEntity())) {
            e.setCanceled(true);
            e.setConversionTimer(600);
         }
      });
      bus.addListener(LivingUseTotemEvent.class, e -> {
         if (isMimic(e.getEntity()) && e.getSource().getEntity() instanceof ServerPlayer p) {
            MimicAwards.award(p, "copycat_totem");
         }
      });
      bus.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, Mimics::onTick);
      bus.addListener(RegisterCommandsEvent.class, e -> MimicCommands.register(e.getDispatcher()));
   }

   static boolean isMimic(Entity e) {
      return e instanceof Mob && e.getTags().contains("cu_mimic");
   }

   static UUID sourceOf(Mob mob) {
      CompoundTag pd = mob.getPersistentData();
      return pd.hasUUID("cu_mimic_src") ? pd.getUUID("cu_mimic_src") : null;
   }

   static int floorOf(Collection<String> tags) {
      for (String t : tags) {
         if (t.startsWith("cu_floor_")) {
            try {
               return Integer.parseInt(t.substring("cu_floor_".length()));
            } catch (NumberFormatException var4) {
               return -1;
            }
         }
      }

      return -1;
   }

   static CompoundTag entityTag(int floor, boolean top) {
      CompoundTag t = new CompoundTag();
      t.putString("id", "minecraft:zombie");
      ListTag tags = new ListTag();
      tags.add(StringTag.valueOf("cu_mimic"));
      if (floor > 0) {
         tags.add(StringTag.valueOf("cu_floor_" + floor));
      }

      if (top) {
         tags.add(StringTag.valueOf("cu_top"));
      }

      t.put("Tags", tags);
      t.putString("CustomName", "\"Mimic\"");
      t.putBoolean("Silent", true);
      t.putBoolean("IsBaby", false);
      t.putBoolean("CanBreakDoors", false);
      t.putBoolean("CanPickUpLoot", false);
      t.putString("DeathLootTable", "minecraft:entities/cow");
      t.put("HandDropChances", zeros(2));
      t.put("ArmorDropChances", zeros(4));
      return t;
   }

   private static ListTag zeros(int n) {
      ListTag l = new ListTag();

      for (int i = 0; i < n; i++) {
         l.add(FloatTag.valueOf(0.0F));
      }

      return l;
   }

   private static void onJoin(EntityJoinLevelEvent e) {
      if (!e.getLevel().isClientSide() && e.getEntity() instanceof Mob mob && mob.getTags().contains("cu_mimic")) {
         CompoundTag pd = mob.getPersistentData();
         if (!pd.hasUUID("cu_mimic_fake")) {
            setup(mob, pd);
         }

         String name = pd.getString("cu_mimic_name");
         if (name.isEmpty()) {
            name = "Mimic";
         }

         GameProfile profile = new GameProfile(pd.getUUID("cu_mimic_fake"), name.length() > 16 ? name.substring(0, 16) : name);
         if (pd.contains("cu_mimic_tex")) {
            profile.getProperties()
               .put("textures", new Property("textures", pd.getString("cu_mimic_tex"), pd.contains("cu_mimic_sig") ? pd.getString("cu_mimic_sig") : null));
         }

         BY_ID.put(mob.getId(), new Mimics.Disguise(profile.getId(), profile, sourceOf(mob), mob));
      }
   }

   private static void setup(Mob mob, CompoundTag pd) {
      MinecraftServer server = mob.getServer();
      List<ServerPlayer> players = server == null ? List.of() : server.getPlayerList().getPlayers();
      ServerPlayer src = players.isEmpty() ? null : players.get(mob.getRandom().nextInt(players.size()));
      mob.setSilent(true);
      mob.removeEffect(MobEffects.INVISIBILITY);
      mob.setCanPickUpLoot(false);
      mob.setCustomName(Component.literal("Mimic"));
      mob.setCustomNameVisible(false);
      if (mob instanceof Zombie z) {
         z.setBaby(false);
         z.setCanBreakDoors(false);
         AttributeInstance reinforcements = z.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
         if (reinforcements != null) {
            reinforcements.setBaseValue(0.0);
         }
      }

      if (src != null) {
         for (EquipmentSlot slot : SLOTS) {
            mob.setItemSlot(slot, src.getItemBySlot(slot).copy());
         }

         GameProfile gp = src.getGameProfile();
         pd.putUUID("cu_mimic_src", src.getUUID());
         pd.putString("cu_mimic_name", gp.getName());
         Property tex = textures(gp);
         if (tex != null) {
            pd.putString("cu_mimic_tex", tex.value());
            if (tex.signature() != null) {
               pd.putString("cu_mimic_sig", tex.signature());
            }
         }
      } else {
         pd.putString("cu_mimic_name", "Mimic");
      }

      for (EquipmentSlot slot : SLOTS) {
         mob.setDropChance(slot, 0.0F);
      }

      UUID r = UUID.randomUUID();
      pd.putUUID("cu_mimic_fake", new UUID(r.getMostSignificantBits() & -61441L | 8192L, r.getLeastSignificantBits()));
      if (src != null && src.level() == mob.level() && src.distanceToSqr(mob) < 1024.0) {
         MimicAwards.award(src, "is_that_me");
      }
   }

   static Property textures(GameProfile profile) {
      Collection<Property> found = profile.getProperties().get("textures");
      return found.isEmpty() ? null : found.iterator().next();
   }

   private static void onHurt(Post e) {
      LivingEntity le = e.getEntity();
      if (!le.level().isClientSide() && le.isAlive() && isMimic(le)) {
         le.level()
            .playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.PLAYER_HURT, SoundSource.HOSTILE, 1.0F, 0.9F + le.getRandom().nextFloat() * 0.2F);
      }
   }

   private static void onDeath(LivingDeathEvent e) {
      LivingEntity dead = e.getEntity();
      if (!dead.level().isClientSide()) {
         Entity killer = e.getSource().getEntity();
         if (isMimic(dead)) {
            dead.level().playSound(null, dead.getX(), dead.getY(), dead.getZ(), SoundEvents.PLAYER_DEATH, SoundSource.HOSTILE, 1.0F, 1.0F);
            if (killer instanceof ServerPlayer p) {
               MimicAwards.onKill(p, (Mob)dead);
            }
         } else if (dead instanceof ServerPlayer victim && isMimic(killer)) {
            Mob mimic = (Mob)killer;
            MimicAwards.award(victim, "slain_by_mimic");
            if (victim.getUUID().equals(sourceOf(mimic))) {
               MimicAwards.award(victim, "stolen_identity");
               mimic.setCustomName(Component.literal("a Mimic of themselves"));
               END_OF_TICK.add(() -> mimic.setCustomName(Component.literal("Mimic")));
            }
         }
      }
   }

   private static void onDrops(LivingDropsEvent e) {
      if (isMimic(e.getEntity())) {
         e.getDrops().removeIf(item -> {
            ItemStack s = item.getItem();
            return !s.is(Items.BEEF) && !s.is(Items.COOKED_BEEF) && !s.is(Items.LEATHER);
         });
      }
   }

   private static void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
      if (!END_OF_TICK.isEmpty()) {
         List<Runnable> run = new ArrayList<>(END_OF_TICK);
         END_OF_TICK.clear();
         run.forEach(Runnable::run);
      }

      if (++ticks % 40 == 0) {
         MinecraftServer server = e.getServer();

         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            MimicFloors.Floor f = MimicFloors.at(p);
            if (f != null) {
               MimicAwards.onFloor(p, f.number(), f.top());
            }
         }

         for (Mimics.Disguise d : BY_ID.values()) {
            if (d.source() != null && d.mob().isAlive()) {
               ServerPlayer px = server.getPlayerList().getPlayer(d.source());
               if (px != null && px.level() == d.mob().level() && px.distanceToSqr(d.mob()) < 256.0 && px.hasLineOfSight(d.mob())) {
                  MimicAwards.award(px, "is_that_me");
               }
            }
         }
      }
   }

   static record Disguise(UUID fakeId, GameProfile profile, UUID source, Mob mob) {
   }
}
