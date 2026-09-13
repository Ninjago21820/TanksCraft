package com.tankscraft.entity;

import com.tankscraft.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Obus de char : trajectoire tendue, légère gravité, explose à l'impact.
 */
public class ShellEntity extends Projectile {
    private static final int LIFETIME_TICKS = 80;
    /** Rayon de l'explosion à l'impact. */
    private static final float EXPLOSION_RADIUS = 2.0F;

    public ShellEntity(EntityType<? extends ShellEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            this.onHit(hit);
        }

        Vec3 dm = this.getDeltaMovement();
        this.setPos(this.getX() + dm.x, this.getY() + dm.y, this.getZ() + dm.z);

        // orientation du modèle
        double h = dm.horizontalDistance();
        if (h > 1.0E-4) {
            this.setYRot((float) (Mth.atan2(dm.x, dm.z) * Mth.RAD_TO_DEG));
            this.setXRot((float) (Mth.atan2(dm.y, h) * Mth.RAD_TO_DEG));
        }
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        // balistique : traînée faible + un peu de gravité
        this.setDeltaMovement(dm.scale(0.995).add(0.0, -0.015, 0.0));

        if (!this.level().isClientSide) {
            if (this.tickCount > LIFETIME_TICKS) {
                this.discard();
                return;
            }
            if (this.tickCount % 2 == 0) {
                ((ServerLevel) this.level()).sendParticles(ParticleTypes.SMOKE,
                        this.getX() - dm.x, this.getY() - dm.y + 0.1, this.getZ() - dm.z, 1,
                        0.0, 0.0, 0.0, 0.01);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!this.level().isClientSide) {
            this.explode();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            this.explode();
        }
    }

    private void explode() {
        this.level().explode(this, this.getX(), this.getY(), this.getZ(), EXPLOSION_RADIUS, Level.ExplosionInteraction.TNT);
        this.discard();
    }

    @Override
    public boolean canHitEntity(net.minecraft.world.entity.Entity target) {
        // n'entre jamais en collision avec le char qui l'a tiré
        if (target == this.getOwner()) {
            return false;
        }
        if (this.getOwner() != null && target == this.getOwner().getVehicle()) {
            return false;
        }
        return super.canHitEntity(target);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }
}
