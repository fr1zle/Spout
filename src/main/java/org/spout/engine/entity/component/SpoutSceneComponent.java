/*
 * This file is part of Spout.
 *
 * Copyright (c) 2011-2012, Spout LLC <http://www.spout.org/>
 * Spout is licensed under the Spout License Version 1.
 *
 * Spout is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * In addition, 180 days after any changes are published, you can use the
 * software, incorporating those changes, under the terms of the MIT license,
 * as described in the Spout License Version 1.
 *
 * Spout is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License,
 * the MIT license and the Spout License Version 1 along with this program.
 * If not, see <http://www.gnu.org/licenses/> for the GNU Lesser General Public
 * License and see <http://spout.in/licensev1> for the full license, including
 * the MIT license.
 */
package org.spout.engine.entity.component;

import java.util.concurrent.atomic.AtomicReference;

import org.lwjgl.util.vector.Vector3f;
import org.spout.api.ClientOnly;
import org.spout.api.component.impl.SceneComponent;
import org.spout.api.entity.Entity;
import org.spout.api.geo.World;
import org.spout.api.geo.discrete.Point;
import org.spout.api.geo.discrete.Transform;
import org.spout.api.math.GenericMath;
import org.spout.api.math.Quaternion;
import org.spout.api.math.QuaternionMath;
import org.spout.api.math.Vector3;
import org.spout.api.math.VectorMath;
import org.spout.engine.world.SpoutRegion;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.physics.bullet.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.btCollisionObject.CollisionFlags;
import com.badlogic.gdx.physics.bullet.btCollisionObject;
import com.badlogic.gdx.physics.bullet.btCollisionShape;
import com.badlogic.gdx.physics.bullet.btDefaultMotionState;
import com.badlogic.gdx.physics.bullet.btRigidBody;
import com.badlogic.gdx.physics.bullet.btRigidBodyConstructionInfo;
import com.badlogic.gdx.physics.bullet.btRigidBodyFlags;
import com.badlogic.gdx.physics.bullet.btTransform;

/**
 * The Spout implementation of {@link SceneComponent}.
 */
public class SpoutSceneComponent extends SceneComponent {
	public static final int ACTIVE_TAG = 1;
	public static final int ISLAND_SLEEPING = 2;
	public static final int WANTS_DEACTIVATION = 3;
	public static final int DISABLE_DEACTIVATION = 4;
	public static final int DISABLE_SIMULATION = 5;

	private final Transform snapshot = new Transform();
	private final Transform live = new Transform();
	private final AtomicReference<SpoutRegion> simulationRegion = new AtomicReference<SpoutRegion>(null);
	private final Matrix4 PHYSICS_MATRIX = new Matrix4();
	private btRigidBody body;

	//Client/Rendering
	private final Transform render = new Transform();
	private Vector3 position = Vector3.ONE;
	private Quaternion rotate = Quaternion.IDENTITY;
	private Vector3 scale = Vector3.ONE;

	@Override
	public void onAttached() {
		//TODO Player Physics
		//		if (getOwner() instanceof Player) {
		//			throw new IllegalStateException("This component is not designed for Players.");
		//		}
		render.set(live);
	}

	@Override
	public Transform getTransform() {
		return snapshot.copy();
	}
	
	public Transform getLiveTransform() {
		return live.copy();
	}

	@Override
	public SceneComponent setTransform(Transform transform) {
		live.set(transform);
		if (body != null) {
			forcePhysicsUpdate();
		}
		return this;
	}

	@Override
	public boolean isTransformDirty() {
		return !snapshot.equals(live);
	}

	@Override
	public Point getPosition() {
		return snapshot.getPosition();
	}

	@Override
	public SceneComponent setPosition(Point point) {
		live.setPosition(point);
		if (body != null) {
			forcePhysicsUpdate();
		}
		return this;
	}

	@Override
	public boolean isPositionDirty() {
		return !snapshot.getPosition().equals(live.getPosition());
	}

	@Override
	public Quaternion getRotation() {
		return snapshot.getRotation();
	}

	@Override
	public SceneComponent setRotation(Quaternion rotation) {
		live.setRotation(rotation);
		if (body != null) {
			forcePhysicsUpdate();
		}
		return this;
	}

	@Override
	public boolean isRotationDirty() {
		return !snapshot.getRotation().equals(live.getRotation());
	}

	@Override
	public Vector3 getScale() {
		return snapshot.getScale();
	}

	@Override
	public SceneComponent setScale(Vector3 scale) {
		live.setScale(scale);
		return this;
	}

	@Override
	public boolean isScaleDirty() {
		return !snapshot.getScale().equals(live.getScale());
	}

	@Override
	public World getWorld() {
		return getPosition().getWorld();
	}

	@Override
	public boolean isWorldDirty() {
		return !snapshot.getPosition().getWorld().equals(live.getPosition().getWorld());
	}

	@Override
	public SceneComponent translate(Vector3 point) {
		live.translate(point);
		return this;
	}

	@Override
	public SceneComponent rotate(Quaternion rotate) {
		live.rotate(rotate);
		return this;
	}

	@Override
	public SceneComponent scale(Vector3 scale) {
		live.scale(scale);
		return this;
	}

	@Override
	public SceneComponent impulse(Vector3 impulse, Vector3 offset) {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().writeLock().lock();
			body.applyImpulse(VectorMath.toVector3f(impulse), VectorMath.toVector3f(offset));
			return this;
		} finally {
			region.getPhysicsLock().writeLock().unlock();
		}
	}

	@Override
	public SceneComponent impulse(Vector3 impulse) {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().writeLock().lock();
			body.applyCentralImpulse(VectorMath.toVector3f(impulse));
			return this;
		} finally {
			region.getPhysicsLock().writeLock().unlock();
		}
	}

	@Override
	public SceneComponent force(Vector3 force, Vector3 offset) {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().writeLock().lock();
			body.applyForce(VectorMath.toVector3f(force), VectorMath.toVector3f(offset));
			return this;
		} finally {
			region.getPhysicsLock().writeLock().unlock();
		}
	}

	@Override
	public SceneComponent force(Vector3 force) {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().writeLock().lock();
			body.applyCentralForce(VectorMath.toVector3f(force));
			return this;
		} finally {
			region.getPhysicsLock().writeLock().unlock();
		}
	}

	@Override
	public SceneComponent torque(Vector3 torque) {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().writeLock().lock();
			body.applyTorque(VectorMath.toVector3f(torque));
			return this;
		} finally {
			region.getPhysicsLock().writeLock().unlock();
		}
	}

	@Override
	public SceneComponent impulseTorque(Vector3 torque) {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().writeLock().lock();
			body.applyTorqueImpulse(VectorMath.toVector3f(torque));
			return this;
		} finally {
			region.getPhysicsLock().writeLock().unlock();
		}
	}

	@Override
	public SceneComponent dampenMovement(float damp) {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().writeLock().lock();
			body.setDamping(damp, body.getAngularDamping());
			return this;
		} finally {
			region.getPhysicsLock().writeLock().unlock();
		}
	}

	@Override
	public SceneComponent dampenRotation(float damp) {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().writeLock().lock();
			body.setDamping(body.getLinearDamping(), damp);
			return this;
		} finally {
			region.getPhysicsLock().writeLock().unlock();
		}
	}

	@Override
	public btCollisionShape getShape() {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().readLock().lock();
			return body.getCollisionShape();
		} finally {
			region.getPhysicsLock().readLock().unlock();
		}
	}

	@Override
	public SceneComponent setShape(final float mass, final btCollisionShape shape) {
		//TODO: allowing api to setShape more than once could cause tearing/threading issues
		final btRigidBody previous = body;
		//Calculate inertia
		final com.badlogic.gdx.math.Vector3 inertia = new com.badlogic.gdx.math.Vector3();
		shape.calculateLocalInertia(mass, inertia);
		//Construct body blueprint
		PHYSICS_MATRIX.setTranslation(VectorMath.toVector3f(live.getPosition()));
		PHYSICS_MATRIX.set(QuaternionMath.toQuaternionf(live.getRotation()));
		final btRigidBodyConstructionInfo blueprint = new btRigidBodyConstructionInfo(mass, new SpoutMotionState(getOwner(), PHYSICS_MATRIX), shape, inertia);
		body = new btRigidBody(blueprint);
		body.activate();
		final SpoutRegion region = simulationRegion.get();
		if (region != null) {
			try {
				region.getPhysicsLock().writeLock().lock();
				if (previous != null) {
					region.getSimulation().removeRigidBody(previous);
				}
				region.getSimulation().addRigidBody(body);
			} finally {
				region.getPhysicsLock().writeLock().unlock();
			}
		}
		return this;
	}

	@Override
	public float getFriction() {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().readLock().lock();
			return body.getFriction();
		} finally {
			region.getPhysicsLock().readLock().unlock();
		}
	}

	@Override
	public SceneComponent setFriction(float friction) {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().writeLock().lock();
			body.setFriction(friction);
			updatePhysicsSpace();
			return this;
		} finally {
			region.getPhysicsLock().writeLock().unlock();
		}
	}

	@Override
	public float getMass() {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().readLock().lock();
			return body.getInvMass();
		} finally {
			region.getPhysicsLock().readLock().unlock();
		}
	}

	@Override
	public float getRestitution() {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().readLock().lock();
			return body.getRestitution();
		} finally {
			region.getPhysicsLock().readLock().unlock();
		}
	}

	@Override
	public SceneComponent setRestitution(float restitution) {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().writeLock().lock();
			body.setRestitution(restitution);
			updatePhysicsSpace();
			return this;
		} finally {
			region.getPhysicsLock().writeLock().unlock();
		}
	}

	@Override
	public Vector3 getMovementVelocity() {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().readLock().lock();
			//TODO Snapshot/live values needed?
			return VectorMath.toVector3(body.getLinearVelocity());
		} finally {
			region.getPhysicsLock().readLock().unlock();
		}
	}

	@Override
	public SceneComponent setMovementVelocity(Vector3 velocity) {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().writeLock().lock();
			body.setLinearVelocity(VectorMath.toVector3f(velocity));
			//TODO May need to perform a Physics space update...testing needed.
			return this;
		} finally {
			region.getPhysicsLock().writeLock().unlock();
		}
	}

	@Override
	public Vector3 getRotationVelocity() {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().readLock().lock();
			//TODO Snapshot/live values needed?
			return VectorMath.toVector3(body.getAngularVelocity());
		} finally {
			region.getPhysicsLock().readLock().unlock();
		}
	}

	@Override
	public SceneComponent setRotationVelocity(Vector3 velocity) {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().writeLock().lock();
			body.setAngularVelocity(VectorMath.toVector3f(velocity));
			//TODO May need to perform a Physics space update...testing needed.
			return this;
		} finally {
			region.getPhysicsLock().writeLock().unlock();
		}
	}

	@Override
	public SceneComponent setActivated(boolean activate) {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().writeLock().lock();
			body.setActivationState(activate == true ? ACTIVE_TAG : DISABLE_SIMULATION);
			return this;
		} finally {
			region.getPhysicsLock().writeLock().unlock();
		}
	}

	@Override
	public boolean isActivated() {
		return body != null && body.getActivationState() == ACTIVE_TAG;
	}

	/**
	 * Gets the live transform state of this {@link org.spout.api.entity.Entity} within the scene.
	 * <p/>
	 * Keep in mind it is completely unstable; the API can change it at anytime during Stage 1 of the
	 * tick.
	 * @return The Transform representing the live state.
	 */
	public Transform getTransformLive() {
		return live;
	}

	/**
	 * Gets the {@link Transform} this {@link org.spout.api.entity.Entity} had within the last game tick
	 * of the scene with interpolation applied (so it appears smooth to users).
	 * <p/>
	 * The render transform is simply the transform of the entity clients see within the scene.
	 * @return The Transform, interpolated, as of the last game tick.
	 */
	@ClientOnly
	public Transform getRenderTransform() {
		return render;
	}

	/**
	 * Gets the {@link btRigidBody} that this {@link Entity} has within Physics space.
	 * @return The collision object.
	 */
	public btRigidBody getBody() {
		return body;
	}

	/**
	 * Snapshots values for the next tick.
	 */
	public void copySnapshot() {
		snapshot.set(live);

		//Have Spout interpolate if this Entity has no valid body.
		if (body == null) {
			position = snapshot.getPosition();
			scale = snapshot.getScale();
			
			Quaternion qDiff = new Quaternion(	snapshot.getRotation().getX()-render.getRotation().getX(),
												snapshot.getRotation().getY()-render.getRotation().getY(),
												snapshot.getRotation().getZ()-render.getRotation().getZ(),
												snapshot.getRotation().getW()-render.getRotation().getW(),false);

			if (qDiff.getX()*qDiff.getX()+qDiff.getY()*qDiff.getY()+qDiff.getZ()*qDiff.getZ() > 2){
				rotate = new Quaternion(-snapshot.getRotation().getX(),
										-snapshot.getRotation().getY(),
										-snapshot.getRotation().getZ(),
										-snapshot.getRotation().getW(),false);
			}
			else
				rotate = snapshot.getRotation();
		}
	}

	/**
	 * Interpolates the render transform for Spout rendering. This only kicks in when the entity has no body.
	 * @param dtp time since last interpolation.
	 */
	public void interpolateRender(float dtp) {
		
		float dt = dtp*80f/20f;
		
		render.setPosition(render.getPosition().multiply(1-dt).add(position.multiply(dt)));
		Quaternion q = render.getRotation();
		render.setRotation(new Quaternion(	q.getX()*(1-dt) + rotate.getX()*dt,
													q.getY()*(1-dt) + rotate.getY()*dt,
													q.getZ()*(1-dt) + rotate.getZ()*dt,
													q.getW()*(1-dt) + rotate.getW()*dt,false));
		render.setScale(render.getScale().multiply(1-dt).add(scale.multiply(dt)));
	}

	/**
	 * Updates a body within the simulation.
	 * <p/>
	 * Due to how TeraBullet caches bodies in the simulation, updating attributes of a body tend to have no
	 * effect as it uses cached values. This method does a workaround by hotswapping bodies.
	 * <p/>
	 * This method should be entirely safe to use as physics isn't ticked until Stage 2, this method is only
	 * available in Stage 1.
	 * <p/>
	 * TODO See if clearing cache pairs solves this without hotswapping?
	 */
	private void updatePhysicsSpace() {
		final SpoutRegion region = simulationRegion.get();
		//swap
		region.getSimulation().removeRigidBody(body);
		region.getSimulation().addRigidBody(body);
	}

	public void simulate(SpoutRegion region) {
		SpoutRegion previous = simulationRegion.getAndSet(region);
		if (previous != region && body != null) {
			try {
				region.getPhysicsLock().writeLock().lock();
				region.getSimulation().addRigidBody(body);
			} finally {
				region.getPhysicsLock().writeLock().unlock();
			}
		}
	}

	/**
	 * Checks to see if the body isn't null and if so, throws an exception.
	 */
	private void validateBody(final SpoutRegion region) {
		if (body == null) {
			throw new IllegalStateException("You need to give the Entity a shape (with setShape(mass, shape) before manipulating it");
		}
		if (region == null) {
			throw new IllegalStateException("Attempting to update a body within a simulation but the region is null!");
		}
	}

	/**
	 * Forces a physics body translation without forces or any physics corrections.
	 */
	private void forcePhysicsUpdate() {
		final SpoutRegion region = simulationRegion.get();
		validateBody(region);
		try {
			region.getPhysicsLock().writeLock().lock();
			PHYSICS_MATRIX.setTranslation(VectorMath.toVector3f(live.getPosition()));
			PHYSICS_MATRIX.set(QuaternionMath.toQuaternionf(live.getRotation()));
			body.setWorldTransform(PHYSICS_MATRIX);
			body.clearForces(); //TODO May not be correct here, needs testing.
		} finally {
			region.getPhysicsLock().writeLock().unlock();
		}
	}

	private final class SpoutMotionState extends btDefaultMotionState {
		private final SpoutSceneComponent scene;

		public SpoutMotionState(Entity entity, Matrix4 startingTransform) {
			super(startingTransform);
			this.scene = (SpoutSceneComponent) entity.getScene();
		}

		@Override
		public void setWorldTransform (final Matrix4 worldTrans) {
			final com.badlogic.gdx.math.Vector3 physicsPosition = new com.badlogic.gdx.math.Vector3();
			final com.badlogic.gdx.math.Quaternion physicsRotation = new com.badlogic.gdx.math.Quaternion();
			worldTrans.getTranslation(physicsPosition);
			worldTrans.getRotation(physicsRotation);
			final Transform live = scene.getTransformLive();
			live.setPosition(new Point(VectorMath.toVector3(physicsPosition), live.getPosition().getWorld()));
			live.setRotation(QuaternionMath.toQuaternion(physicsRotation));
		}

		@Override
		public void getWorldTransform(final Matrix4 worldTrans) {
			final Transform live = scene.getTransformLive();
			PHYSICS_MATRIX.setTranslation(VectorMath.toVector3f(live.getPosition()));
			PHYSICS_MATRIX.set(QuaternionMath.toQuaternionf(live.getRotation()));
			worldTrans.set(PHYSICS_MATRIX);
		}
	}
}