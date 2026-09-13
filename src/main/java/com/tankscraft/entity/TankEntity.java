package com.tankscraft.entity;

import com.tankscraft.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Un char pilotable. Le pilotage suit le modèle vanilla des bateaux :
 * le client qui contrôle simule la physique (entrée clavier) et la position
 * est synchronisée au serveur via les paquets "vehicle move" automatiques.
 *
 * La tourelle suit la vue du pilote (rendue par {@code TankRenderer}).
 */
public class TankEntity extends Entity {
    private static final float MAX_HEALTH = 400.0F;
    /** Accélération en blocs/tick². */
    private static final double ACCEL = 0.012;
    /** Vitesse de pointe (blocs/tick) ~ 43 km/h. */
    private static final double MAX_SPEED = 0.6;
    /** Vitesse de pointe en marche arrière. */
    private static final double MAX_REVERSE = 0.28;
    /** Rotation en degrés/tick en roulant. */
    private static final float TURN_RATE = 2.2F;
    /** Rotation sur place (pivot), plus lente. */
    private static final float PIVOT_RATE = 1.4F;
    /** Temps de rechargement du canon (ticks). */
    private static final int RELOAD_TICKS = 60;

    private float health = MAX_HEALTH;
    /** Vitesse longitudinale courante (blocs/tick), négative en marche arrière. */
    private double driveSpeed = 0.0;
    /** Entrées du pilote, remplies côté client par ClientEvents. */
    private boolean inputForward, inputBack, inputLeft, inputRight;
    private int fireCooldown = 0;

    public TankEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    // ---------------------------------------------------------------- pilotage

    public void setClientInput(boolean forward, boolean back, boolean left, boolean right) {
        this.inputForward = forward;
        this.inputBack = back;
        this.inputLeft = left;
        this.inputRight = right;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.fireCooldown > 0) {
            this.fireCooldown--;
        }
        if (this.isControlledByLocalInstance()) {
            this.driveTick();
        }
        if (this.level().isClientSide && Math.abs(this.driveSpeed) > 0.08) {
            this.spawnExhaustSmoke();
        }
    }

    /** Simulation locale : uniquement sur le client qui contrôle, ou sur le serveur sans pilote. */
    private void driveTick() {
        if (this.getControllingPassenger() == null) {
            this.idlePhysics();
            return;
        }

        float throttle = (this.inputForward ? 1.0F : 0.0F) - (this.inputBack ? 1.0F : 0.0F);
        float steer = (this.inputLeft ? 1.0F : 0.0F) - (this.inputRight ? 1.0F : 0.0F);

        if (steer != 0.0F) {
            float rate = throttle != 0.0F ? TURN_RATE : PIVOT_RATE;
            float turn = -steer * rate; // tourner à gauche = yaw décroissant
            if (throttle < 0.0F) {
                turn = -turn; // en marche arrière, l'inversion du sens de conduite
            }
            this.setYRot(this.getYRot() + turn);
        }

        if (throttle > 0.0F) {
            this.driveSpeed = Math.min(MAX_SPEED, this.driveSpeed + ACCEL);
        } else if (throttle < 0.0F) {
            this.driveSpeed = Math.max(-MAX_REVERSE, this.driveSpeed - ACCEL * 1.4);
        } else {
            this.driveSpeed *= 0.86;
        }

        this.applyMovementPhysics();
    }

    /** Gravité/friction sans pilote. */
    private void idlePhysics() {
        this.driveSpeed *= 0.6;
        this.applyMovementPhysics();
    }

    private void applyMovementPhysics() {
        Vec3 forward = forwardFromYaw(this.getYRot());
        Vec3 dm = this.getDeltaMovement();
        double vy = dm.y * 0.98 - 0.04; // gravité
        this.setDeltaMovement(forward.x * this.driveSpeed, vy, forward.z * this.driveSpeed);
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (this.horizontalCollision && Math.abs(this.driveSpeed) > 0.25) {
            this.driveSpeed = 0.0;
        }
    }

    public static Vec3 forwardFromYaw(float yawDeg) {
        float rad = yawDeg * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(rad), 0.0, Mth.cos(rad));
    }

    private void spawnExhaustSmoke() {
        if (this.tickCount % 3 == 0) {
            Vec3 back = forwardFromYaw(this.getYRot()).scale(-1.1);
            this.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    this.getX() + back.x, this.getY() + 1.0, this.getZ() + back.z,
                    0.0, 0.03, 0.0);
        }
    }

    // ---------------------------------------------------------------- canon

    /** Tire un obus depuis la bouche du canon, côté serveur. */
    public void fire(Player gunner) {
        if (this.fireCooldown > 0 || this.level().isClientSide) {
            return;
        }
        this.fireCooldown = RELOAD_TICKS;

        Vec3 aim = gunner.getViewVector(1.0F);
        Vec3 muzzle = this.position().add(0.0, 1.45, 0.0).add(aim.normalize().scale(2.6));

        ShellEntity shell = new ShellEntity(ModEntities.SHELL.get(), this.level());
        shell.setPos(muzzle.x, muzzle.y, muzzle.z);
        shell.setDeltaMovement(aim.normalize().scale(2.2));
        shell.setOwner(gunner);
        if (!this.level().addFreshEntity(shell)) {
            return;
        }

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 2.5F, 0.35F);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    public boolean isReloading() {
        return this.fireCooldown > 0;
    }

    // ---------------------------------------------------------------- dégâts

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        // pas de tir ami : on ignore les obus tirés par un passager de ce char
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof LivingEntity shooter
                && shooter.getVehicle() == this) {
            return false;
        }
        this.health -= amount;
        if (this.health <= 0.0F) {
            this.detonate();
        }
        return true;
    }

    protected void detonate() {
        if (!this.level().isClientSide) {
            this.level().explode(this, this.getX(), this.getY() + 0.8, this.getZ(), 3.0F, Level.ExplosionInteraction.TNT);
            this.ejectPassengers();
        }
        this.discard();
    }

    public float getTankHealth() {
        return this.health;
    }

    // ---------------------------------------------------------------- passagers

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!this.isVehicle()) {
            if (!this.level().isClientSide) {
                return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (this.getFirstPassenger() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return new Vec3(this.getX(), this.getY() + 1.0, this.getZ());
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < 1;
    }

    // ---------------------------------------------------------------- divers

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Health")) {
            this.health = tag.getFloat("Health");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Health", this.health);
    }

    @Override
    protected void defineSynchedEntityData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }
}
