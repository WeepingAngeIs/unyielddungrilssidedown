package dev.unyielding.upsidedown;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import dev.unyielding.upsidedown.client.ClientFlip;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Pre;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod("cuupsidedown")
public final class UpsideDown {
   public static final String MODID = "cuupsidedown";
   public static final Logger LOG = LogUtils.getLogger();
   private static MinecraftServer server;
   private static final SuggestionProvider<CommandSourceStack> GRAVITIES = (c, b) -> SharedSuggestionProvider.suggest(
         new String[]{"up", "down", "north", "south", "east", "west"}, b
      );
   private static final SuggestionProvider<CommandSourceStack> ZONE_NAMES = (c, b) -> SharedSuggestionProvider.suggest(
         Flip.SERVER.stream().map(Flip.Zone::name), b
      );
   private static final SuggestionProvider<CommandSourceStack> LAYOUT_IDS = (c, b) -> SharedSuggestionProvider.suggestResource(Layouts.LAYOUTS.keySet(), b);
   private static final SuggestionProvider<CommandSourceStack> ROTATIONS = (c, b) -> SharedSuggestionProvider.suggest(
         new String[]{"none", "clockwise_90", "180", "counterclockwise_90"}, b
      );

   public UpsideDown(IEventBus modBus, ModContainer container) {
      dev.unyielding.upsidedown.dungeon.DungeonModule.init(modBus);
      container.registerConfig(Type.SERVER, Config.SERVER_SPEC);
      container.registerConfig(Type.CLIENT, Config.CLIENT_SPEC);
      modBus.addListener(RegisterPayloadHandlersEvent.class, e -> {
         PayloadRegistrar r = e.registrar("2");
         r.playToClient(ZonesPayload.TYPE, ZonesPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> Flip.setClient(p.zones())));
         r.playToClient(GravityPayloads.Sync.TYPE, GravityPayloads.Sync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
               if (FMLEnvironment.dist == Dist.CLIENT) {
                  ClientFlip.onSync(p);
               }
            }));
         r.playToServer(GravityPayloads.Change.TYPE, GravityPayloads.Change.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
               if (ctx.player() instanceof ServerPlayer sp) {
                  Gravity.onChange(sp, p);
               }
            }));
      });
      IEventBus bus = NeoForge.EVENT_BUS;
      bus.addListener(ServerStartedEvent.class, e -> {
         server = e.getServer();
         Flip.setServer(ZoneData.get(server).zones);
         dev.unyielding.upsidedown.dungeon.DungeonModule.afterZonesLoaded(server);
      });
      bus.addListener(ServerStoppedEvent.class, e -> {
         server = null;
         Flip.setServer(List.of());
      });
      bus.addListener(AddReloadListenerEvent.class, e -> e.addListener(new Layouts()));
      bus.addListener(PlayerLoggedInEvent.class, e -> {
         sync(e.getEntity());
         if (e.getEntity() instanceof ServerPlayer sp) {
            Gravity.setRaw(sp, Gravity.saved(sp));
            Gravity.sendState(sp, Gravity.exempt(sp) ? 4 : 0);
         }
      });
      bus.addListener(PlayerChangedDimensionEvent.class, e -> {
         sync(e.getEntity());
         if (e.getEntity() instanceof ServerPlayer sp) {
            Gravity.sendState(sp, 0);
         }
      });
      bus.addListener(PlayerRespawnEvent.class, e -> {
         sync(e.getEntity());
         if (e.getEntity() instanceof ServerPlayer sp) {
            Gravity.setRaw(sp, Direction.DOWN);
            Gravity.sendState(sp, Gravity.exempt(sp) ? 4 : 0);
         }
      });
      bus.addListener(StartTracking.class, e -> {
         Entity t = e.getTarget();
         if (Gravity.of(t) != Direction.DOWN && e.getEntity() instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new GravityPayloads.Sync(t.getId(), Gravity.of(t), 2), new CustomPacketPayload[0]);
         }
      });
      bus.addListener(EntityJoinLevelEvent.class, e -> {
         Entity en = e.getEntity();
         if (!e.getLevel().isClientSide() && !(en instanceof Player)) {
            Direction s = Gravity.saved(en);
            if (s != Direction.DOWN) {
               Gravity.setRaw(en, s);
            }
         }
      });
      bus.addListener(Pre.class, e -> {
         if (!e.getEntity().level().isClientSide()) {
            ((GravityEntity)e.getEntity()).cu$setTravelDepth(0);
            Gravity.serverTick(e.getEntity());
         }
      });
      bus.addListener(Post.class, UpsideDown::afterTick);
      bus.addListener(RegisterCommandsEvent.class, e -> commands(e.getDispatcher()));
      if (FMLEnvironment.dist == Dist.CLIENT) {
         ClientFlip.init(modBus);
      }
   }

   private static void sync(Player p) {
      if (p instanceof ServerPlayer sp) {
         PacketDistributor.sendToPlayer(sp, new ZonesPayload(Flip.SERVER), new CustomPacketPayload[0]);
      }
   }

   static void publish(MinecraftServer srv, List<Flip.Zone> zones) {
      ZoneData d = ZoneData.get(srv);
      d.zones.clear();
      d.zones.addAll(zones);
      d.setDirty();
      Flip.setServer(zones);
      PacketDistributor.sendToAllPlayers(new ZonesPayload(Flip.SERVER), new CustomPacketPayload[0]);
   }

   private static void afterTick(Post e) {
      Entity en = e.getEntity();
      if (!en.level().isClientSide() && (!Flip.SERVER.isEmpty() || en.getPersistentData().getBoolean("cu_flipped"))) {
         boolean now = Flip.isFlipped(en);
         boolean was = en.getPersistentData().getBoolean("cu_flipped");
         if (now != was) {
            en.getPersistentData().putBoolean("cu_flipped", now);
            en.resetFallDistance();
            if (en instanceof LivingEntity le) {
               le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 50, 0, false, false, false));
            }

            if (now) {
               double roof = Flip.roofOf(en);
               AABB b = en.getBoundingBox();
               if (!Double.isNaN(roof) && b.maxY > roof + 1.0E-4) {
                  double dy = roof - b.maxY;
                  if (en.level().noCollision(en, b.move(0.0, dy, 0.0))) {
                     if (en instanceof ServerPlayer sp) {
                        sp.connection.teleport(sp.getX(), sp.getY() + dy, sp.getZ(), sp.getYRot(), sp.getXRot());
                     } else {
                        en.teleportTo(en.getX(), en.getY() + dy, en.getZ());
                     }
                  }
               }
            }
         }
      }
   }

   private static Direction gravityArg(String s) throws CommandSyntaxException {
      Direction d = Direction.byName(s.toLowerCase(Locale.ROOT));
      if (d == null) {
         throw new SimpleCommandExceptionType(Component.literal("Gravity must be up, down, north, south, east or west")).create();
      } else {
         return d;
      }
   }

   private static Rotation rotationArg(String s) throws CommandSyntaxException {
      String var1 = s.toLowerCase(Locale.ROOT);

      return switch (var1) {
         case "none", "0" -> Rotation.NONE;
         case "clockwise_90", "cw90", "90" -> Rotation.CLOCKWISE_90;
         case "180", "clockwise_180" -> Rotation.CLOCKWISE_180;
         case "counterclockwise_90", "ccw90", "270" -> Rotation.COUNTERCLOCKWISE_90;
         default -> throw new SimpleCommandExceptionType(
            Component.literal("Rotation must be none, clockwise_90, 180 or counterclockwise_90 (same as you pasted the structure with)")
         )
         .create();
      };
   }

   private static int addZone(CommandContext<CommandSourceStack> c, Direction g) throws CommandSyntaxException {
      BlockPos a = BlockPosArgument.getBlockPos(c, "from");
      BlockPos b = BlockPosArgument.getBlockPos(c, "to");
      String name = StringArgumentType.getString(c, "name");
      AABB box = new AABB(
         (double)Math.min(a.getX(), b.getX()),
         (double)Math.min(a.getY(), b.getY()),
         (double)Math.min(a.getZ(), b.getZ()),
         (double)(Math.max(a.getX(), b.getX()) + 1),
         (double)(Math.max(a.getY(), b.getY()) + 1),
         (double)(Math.max(a.getZ(), b.getZ()) + 1)
      );
      List<Flip.Zone> l = new ArrayList<>(Flip.SERVER);
      l.removeIf(z -> z.name().equals(name));
      l.add(new Flip.Zone(name, ((CommandSourceStack)c.getSource()).getLevel().dimension(), box, g));
      publish(((CommandSourceStack)c.getSource()).getServer(), l);

      String what = switch (g) {
         case UP -> "gravity is upside down inside it.";
         case DOWN -> "normal gravity inside it.";
         default -> "gravity pulls " + g.getSerializedName() + " inside it.";
      };
      ((CommandSourceStack)c.getSource()).sendSuccess(() -> Component.literal("Gravity zone '" + name + "' set: " + what), true);
      return 1;
   }

   private static int placeLayout(CommandContext<CommandSourceStack> c, Rotation given) throws CommandSyntaxException {
      ResourceLocation id = ResourceLocationArgument.getId(c, "layout");
      Layouts.Layout lay = Layouts.LAYOUTS.get(id);
      if (lay == null) {
         ((CommandSourceStack)c.getSource()).sendFailure(Component.literal("No gravity layout called " + id + " (try /flipzone layout list)"));
         return 0;
      } else {
         CommandSourceStack src = (CommandSourceStack)c.getSource();
         ServerLevel level = src.getLevel();
         Vec3 at = src.getPosition();
         BlockPos origin = BlockPos.containing(at);
         boolean marker = level.getBlockState(origin).getBlock() instanceof CommandBlock;
         if (!marker) {
            BlockPos feet = BlockPos.containing(at.x, at.y - 0.5, at.z);
            BlockPos found = null;
            double best = Double.MAX_VALUE;

            for (BlockPos q : BlockPos.betweenClosed(feet.offset(-2, -2, -2), feet.offset(2, 1, 2))) {
               if (level.getBlockState(q).getBlock() instanceof CommandBlock) {
                  BlockEntity a = level.getBlockEntity(q);
                  if (a instanceof CommandBlockEntity) {
                     CommandBlockEntity cb = (CommandBlockEntity)a;
                     if (cb.getCommandBlock().getCommand().contains(id.getPath())) {
                        double dd = q.distToCenterSqr(at);
                        if (dd < best) {
                           best = dd;
                           found = q.immutable();
                        }
                     }
                  }
               }
            }

            if (found != null) {
               origin = found;
               marker = true;
            } else {
               src.sendSystemMessage(Component.literal("No marker command block for " + id + " next to you: zones are placed from where you stand."));
            }
         }

         Rotation rot = given == null ? Rotation.NONE : given;
         if (given == null && marker && lay.markerFacing() != null) {
            Direction f = (Direction)level.getBlockState(origin).getValue(CommandBlock.FACING);
            if (f.getAxis().isHorizontal()) {
               Direction m = lay.markerFacing();
               rot = f == m
                  ? Rotation.NONE
                  : (f == m.getClockWise() ? Rotation.CLOCKWISE_90 : (f == m.getOpposite() ? Rotation.CLOCKWISE_180 : Rotation.COUNTERCLOCKWISE_90));
            }
         }

         Rotation used = rot;
         BlockPos o = origin;
         String prefix = id.getPath().replace('/', '_') + ".";
         List<Flip.Zone> l = new ArrayList<>(Flip.SERVER);
         l.removeIf(z -> z.name().startsWith(prefix));

         for (Layouts.ZoneDef zd : lay.zones()) {
            BlockPos a = origin.offset(Layouts.turn(zd.from(), rot));
            BlockPos b = origin.offset(Layouts.turn(zd.to(), rot));
            AABB box = new AABB(
               (double)Math.min(a.getX(), b.getX()),
               (double)Math.min(a.getY(), b.getY()),
               (double)Math.min(a.getZ(), b.getZ()),
               (double)(Math.max(a.getX(), b.getX()) + 1),
               (double)(Math.max(a.getY(), b.getY()) + 1),
               (double)(Math.max(a.getZ(), b.getZ()) + 1)
            );
            l.add(new Flip.Zone(prefix + zd.name(), src.getLevel().dimension(), box, Layouts.turn(zd.gravity(), rot)));
         }

         publish(src.getServer(), l);
         ZoneData d = ZoneData.get(src.getServer());
         d.anchors.removeIf(an -> an.name().equals(id.toString()));
         d.anchors.add(new ZoneData.Anchor(id.toString(), src.getLevel().dimension(), origin.offset(Layouts.turn(lay.entrance(), rot))));
         d.setDirty();
         int n = lay.zones().size();
         src.sendSuccess(
            () -> Component.literal(
                  "Placed gravity layout "
                     + id
                     + ": "
                     + n
                     + " zones (named "
                     + prefix
                     + "*) at "
                     + o.getX()
                     + " "
                     + o.getY()
                     + " "
                     + o.getZ()
                     + (used == Rotation.NONE ? "" : ", turned " + used.getSerializedName())
                     + "."
               ),
            true
         );
         return n;
      }
   }

   private static int resetPlayers(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> ps) {
      for (ServerPlayer sp : ps) {
         ((GravityEntity)sp).cu$setGrace(60);
         if (!Gravity.change(sp, Direction.DOWN)) {
            Gravity.setRaw(sp, Direction.DOWN);
         }

         PacketDistributor.sendToPlayer(sp, new GravityPayloads.Sync(sp.getId(), Direction.DOWN, 18), new CustomPacketPayload[0]);
         sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), sp.getXRot());
         PacketDistributor.sendToPlayersTrackingEntity(sp, new GravityPayloads.Sync(sp.getId(), Direction.DOWN, 2), new CustomPacketPayload[0]);
      }

      ((CommandSourceStack)c.getSource())
         .sendSuccess(() -> Component.literal("Reset gravity to normal for " + ps.size() + " player(s) (zones ignored for 3 seconds)."), true);
      return ps.size();
   }

   private static void commands(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                              "flipzone"
                           )
                           .requires(s -> s.hasPermission(2)))
                        .then(
                           Commands.literal("add")
                              .then(
                                 Commands.argument("name", StringArgumentType.word())
                                    .then(
                                       Commands.argument("from", BlockPosArgument.blockPos())
                                          .then(
                                             ((RequiredArgumentBuilder)Commands.argument("to", BlockPosArgument.blockPos())
                                                   .executes(c -> addZone(c, Direction.UP)))
                                                .then(
                                                   Commands.argument("gravity", StringArgumentType.word())
                                                      .suggests(GRAVITIES)
                                                      .executes(c -> addZone(c, gravityArg(StringArgumentType.getString(c, "gravity"))))
                                                )
                                          )
                                    )
                              )
                        ))
                     .then(
                        Commands.literal("remove")
                           .then(
                              Commands.argument("name", StringArgumentType.word())
                                 .suggests(ZONE_NAMES)
                                 .executes(
                                    c -> {
                                       String name = StringArgumentType.getString(c, "name");
                                       List<Flip.Zone> l = new ArrayList<>(Flip.SERVER);
                                       boolean gone = l.removeIf(z -> z.name().equals(name));
                                       publish(((CommandSourceStack)c.getSource()).getServer(), l);
                                       ((CommandSourceStack)c.getSource())
                                          .sendSuccess(() -> Component.literal(gone ? "Removed zone '" + name + "'." : "No zone called '" + name + "'."), true);
                                       return gone ? 1 : 0;
                                    }
                                 )
                           )
                     ))
                  .then(Commands.literal("removeall").then(Commands.argument("prefix", StringArgumentType.word()).executes(c -> {
                     String pre = StringArgumentType.getString(c, "prefix");
                     List<Flip.Zone> l = new ArrayList<>(Flip.SERVER);
                     int before = l.size();
                     l.removeIf(z -> z.name().startsWith(pre));
                     int n = before - l.size();
                     publish(((CommandSourceStack)c.getSource()).getServer(), l);
                     ((CommandSourceStack)c.getSource()).sendSuccess(() -> Component.literal("Removed " + n + " zone(s) starting with '" + pre + "'."), true);
                     return n;
                  }))))
               .then(
                  Commands.literal("list")
                     .executes(
                        c -> {
                           if (Flip.SERVER.isEmpty()) {
                              ((CommandSourceStack)c.getSource()).sendSuccess(() -> Component.literal("No gravity zones."), false);
                           }

                           for (Flip.Zone z : Flip.SERVER) {
                              AABB b = z.box();
                              ((CommandSourceStack)c.getSource())
                                 .sendSuccess(
                                    () -> Component.literal(
                                          String.format(
                                             "%s [%s] in %s: %d %d %d -> %d %d %d",
                                             z.name(),
                                             z.gravity().getSerializedName(),
                                             z.dim().location(),
                                             (int)b.minX,
                                             (int)b.minY,
                                             (int)b.minZ,
                                             (int)b.maxX - 1,
                                             (int)b.maxY - 1,
                                             (int)b.maxZ - 1
                                          )
                                       ),
                                    false
                                 );
                           }

                           return Flip.SERVER.size();
                        }
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("layout")
                        .then(
                           Commands.literal("list")
                              .executes(
                                 c -> {
                                    if (Layouts.LAYOUTS.isEmpty()) {
                                       ((CommandSourceStack)c.getSource()).sendSuccess(() -> Component.literal("No gravity layouts loaded."), false);
                                    }

                                    for (Layouts.Layout lay : Layouts.LAYOUTS.values()) {
                                       ((CommandSourceStack)c.getSource())
                                          .sendSuccess(() -> Component.literal(lay.id() + " (" + lay.zones().size() + " zones) " + lay.description()), false);
                                    }

                                    return Layouts.LAYOUTS.size();
                                 }
                              )
                        ))
                     .then(
                        Commands.literal("place")
                           .then(
                              ((RequiredArgumentBuilder)Commands.argument("layout", ResourceLocationArgument.id())
                                    .suggests(LAYOUT_IDS)
                                    .executes(c -> placeLayout(c, null)))
                                 .then(
                                    Commands.argument("rotation", StringArgumentType.word())
                                       .suggests(ROTATIONS)
                                       .executes(c -> placeLayout(c, rotationArg(StringArgumentType.getString(c, "rotation"))))
                                 )
                           )
                     ))
                  .then(Commands.literal("remove").then(Commands.argument("layout", ResourceLocationArgument.id()).suggests(LAYOUT_IDS).executes(c -> {
                     ResourceLocation id = ResourceLocationArgument.getId(c, "layout");
                     String pre = id.getPath().replace('/', '_') + ".";
                     List<Flip.Zone> l = new ArrayList<>(Flip.SERVER);
                     int before = l.size();
                     l.removeIf(z -> z.name().startsWith(pre));
                     int n = before - l.size();
                     publish(((CommandSourceStack)c.getSource()).getServer(), l);
                     ZoneData dd = ZoneData.get(((CommandSourceStack)c.getSource()).getServer());
                     dd.anchors.removeIf(an -> an.name().equals(id.toString()));
                     dd.setDirty();
                     ((CommandSourceStack)c.getSource()).sendSuccess(() -> Component.literal("Removed layout " + id + " (" + n + " zones)."), true);
                     return n;
                  })))
            )
      );
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                              "gravity"
                           )
                           .requires(s -> s.hasPermission(2)))
                        .then(
                           ((LiteralArgumentBuilder)Commands.literal("get")
                                 .executes(c -> showGravity(c, List.of(((CommandSourceStack)c.getSource()).getPlayerOrException()))))
                              .then(
                                 Commands.argument("targets", EntityArgument.players()).executes(c -> showGravity(c, EntityArgument.getPlayers(c, "targets")))
                              )
                        ))
                     .then(
                        ((LiteralArgumentBuilder)Commands.literal("reset")
                              .executes(c -> resetPlayers(c, List.of(((CommandSourceStack)c.getSource()).getPlayerOrException()))))
                           .then(Commands.argument("targets", EntityArgument.players()).executes(c -> resetPlayers(c, EntityArgument.getPlayers(c, "targets"))))
                     ))
                  .then(
                     Commands.literal("exempt")
                        .then(
                           Commands.argument("targets", EntityArgument.players())
                              .then(
                                 Commands.argument("on", BoolArgumentType.bool())
                                    .executes(
                                       c -> {
                                          boolean on = BoolArgumentType.getBool(c, "on");
                                          Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "targets");

                                          for (ServerPlayer sp : ps) {
                                             Gravity.setExempt(sp, on);
                                             PacketDistributor.sendToPlayer(
                                                sp, new GravityPayloads.Sync(sp.getId(), Gravity.of(sp), on ? 4 : 8), new CustomPacketPayload[0]
                                             );
                                          }

                                          ((CommandSourceStack)c.getSource())
                                             .sendSuccess(
                                                () -> Component.literal(
                                                      (on ? "Gravity zones now ignored by " : "Gravity zones apply again to ") + ps.size() + " player(s)."
                                                   ),
                                                true
                                             );
                                          return ps.size();
                                       }
                                    )
                              )
                        )
                  ))
               .then(
                  Commands.literal("room")
                     .executes(
                        c -> {
                           Entity en = ((CommandSourceStack)c.getSource()).getEntityOrException();
                           Flip.Zone z = Flip.zoneOf(en);
                           ((CommandSourceStack)c.getSource())
                              .sendSuccess(
                                 () -> Component.literal(
                                       z == null
                                          ? "Not in a gravity zone (normal gravity)."
                                          : "In zone '" + z.name() + "' (gravity " + z.gravity().getSerializedName() + ")."
                                    ),
                                 false
                              );
                           return z == null ? 0 : 1;
                        }
                     )
               ))
            .then(Commands.literal("entrance").then(Commands.argument("layout", ResourceLocationArgument.id()).suggests(LAYOUT_IDS).executes(c -> {
               ResourceLocation id = ResourceLocationArgument.getId(c, "layout");
               ServerPlayer sp = ((CommandSourceStack)c.getSource()).getPlayerOrException();

               for (ZoneData.Anchor an : ZoneData.get(((CommandSourceStack)c.getSource()).getServer()).anchors) {
                  if (an.name().equals(id.toString())) {
                     ServerLevel lvl = ((CommandSourceStack)c.getSource()).getServer().getLevel(an.dim());
                     if (lvl != null) {
                        ((GravityEntity)sp).cu$setGrace(40);
                        Gravity.setRaw(sp, Direction.DOWN);
                        PacketDistributor.sendToPlayer(sp, new GravityPayloads.Sync(sp.getId(), Direction.DOWN, 18), new CustomPacketPayload[0]);
                        Vec3 p = Vec3.atBottomCenterOf(an.entrance());
                        sp.teleportTo(lvl, p.x, p.y, p.z, sp.getYRot(), 0.0F);
                        ((CommandSourceStack)c.getSource()).sendSuccess(() -> Component.literal("Teleported to the entrance of " + id + "."), false);
                        return 1;
                     }
                     break;
                  }
               }

               ((CommandSourceStack)c.getSource()).sendFailure(Component.literal("Layout " + id + " hasn't been placed in this world."));
               return 0;
            })))
      );
   }

   private static int showGravity(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> ps) {
      for (ServerPlayer sp : ps) {
         Flip.Zone z = Flip.zoneOf(sp);
         String ex = Gravity.exempt(sp) ? " (exempt from zones)" : "";
         ((CommandSourceStack)c.getSource())
            .sendSuccess(
               () -> Component.literal(
                     sp.getName().getString()
                        + ": gravity "
                        + Gravity.of(sp).getSerializedName()
                        + (z == null ? ", no zone" : ", zone '" + z.name() + "'")
                        + ex
                  ),
               false
            );
      }

      return ps.size();
   }
}
