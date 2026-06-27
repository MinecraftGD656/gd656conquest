package org.mods.gd656conquest.common.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.mods.gd656conquest.Gd656conquest;

public final class ConquestEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Gd656conquest.MODID);

    public static final RegistryObject<EntityType<SquadDeployBeaconEntity>> SQUAD_DEPLOY_BEACON = ENTITY_TYPES.register(
            "squad_deploy_beacon",
            () -> EntityType.Builder.of(SquadDeployBeaconEntity::new, MobCategory.MISC)
                    .sized(0.75F, 0.75F)
                    .clientTrackingRange(96)
                    .updateInterval(2)
                    .build("squad_deploy_beacon")
    );
    public static final RegistryObject<EntityType<MedicalBoxEntity>> MEDICAL_BOX = ENTITY_TYPES.register(
            "medical_box",
            () -> EntityType.Builder.of(MedicalBoxEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F)
                    .clientTrackingRange(96)
                    .updateInterval(2)
                    .build("medical_box")
    );
    public static final RegistryObject<EntityType<AmmoBoxEntity>> AMMO_BOX = ENTITY_TYPES.register(
            "ammo_box",
            () -> EntityType.Builder.of(AmmoBoxEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F)
                    .clientTrackingRange(96)
                    .updateInterval(2)
                    .build("ammo_box")
    );

    private ConquestEntities() {
    }

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
        eventBus.addListener(ConquestEntities::onEntityAttributeCreation);
    }

    private static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        AttributeSupplier.Builder builder = SquadDeployBeaconEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
        event.put(SQUAD_DEPLOY_BEACON.get(), builder.build());
        AttributeSupplier.Builder medicalBuilder = MedicalBoxEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
        event.put(MEDICAL_BOX.get(), medicalBuilder.build());
        AttributeSupplier.Builder ammoBuilder = AmmoBoxEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
        event.put(AMMO_BOX.get(), ammoBuilder.build());
    }
}
