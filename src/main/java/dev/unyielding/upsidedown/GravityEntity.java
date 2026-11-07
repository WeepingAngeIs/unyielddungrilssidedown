package dev.unyielding.upsidedown;

import net.minecraft.core.Direction;

public interface GravityEntity {
   Direction cu$gravity();

   void cu$setGravityRaw(Direction var1);

   int cu$travelDepth();

   void cu$setTravelDepth(int var1);

   int cu$grace();

   void cu$setGrace(int var1);

   int cu$mismatch();

   void cu$setMismatch(int var1);
}
