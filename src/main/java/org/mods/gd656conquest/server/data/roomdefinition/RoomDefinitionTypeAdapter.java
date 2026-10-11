package org.mods.gd656conquest.server.data.roomdefinition;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.mods.gd656conquest.server.room.model.IRoomGameplayConfig;
import org.mods.gd656conquest.server.room.model.RoomBreakthroughConfig;
import org.mods.gd656conquest.server.room.model.RoomConquestConfig;
import org.mods.gd656conquest.server.room.model.RoomDeathmatchConfig;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGunmasterConfig;

import java.lang.reflect.Type;

public final class RoomDefinitionTypeAdapter implements JsonDeserializer<RoomDefinition>, JsonSerializer<RoomDefinition> {
    @Override
    public RoomDefinition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        String gameplayId = obj.has("gameplayId") ? obj.get("gameplayId").getAsString() : "conquest";
        
        // Extract common properties
        String roomId = obj.get("roomId").getAsString();
        String displayName = obj.has("displayName") ? obj.get("displayName").getAsString() : roomId;
        org.mods.gd656conquest.server.room.model.RoomGameplayId id = new org.mods.gd656conquest.server.room.model.RoomGameplayId(gameplayId);
        org.mods.gd656conquest.server.room.model.RoomLifecycleState state = obj.has("lifecycleState") 
            ? org.mods.gd656conquest.server.room.model.RoomLifecycleState.valueOf(obj.get("lifecycleState").getAsString())
            : org.mods.gd656conquest.server.room.model.RoomLifecycleState.IDLE;
            
        org.mods.gd656conquest.server.room.model.RoomCommonConfig commonConfig = context.deserialize(
            obj.get("commonConfig"), 
            org.mods.gd656conquest.server.room.model.RoomCommonConfig.class
        );
        
        IRoomGameplayConfig gameplayConfig;
        JsonElement configElement = obj.get("gameplayConfig");
        
        if ("breakthrough".equalsIgnoreCase(gameplayId)) {
            // Preserve the old proportional value when reading existing room files.
            // Serialize only the new fields, so subsequent saves cannot reconvert it.
            if (configElement != null && configElement.isJsonObject()) {
                JsonObject config = configElement.getAsJsonObject().deepCopy();
                if (!config.has("troopGainPerDefenderKill")) config.addProperty("troopGainPerDefenderKill", 3);
                if ((!config.has("troopsPerPlayer") || config.get("troopsPerPlayer").isJsonNull())
                    && config.has("maxAttackTroopsAt12Players") && !config.get("maxAttackTroopsAt12Players").isJsonNull()) {
                    config.addProperty("troopsPerPlayer", config.get("maxAttackTroopsAt12Players").getAsDouble() / 12.0D);
                }
                configElement = config;
            }
            gameplayConfig = context.deserialize(configElement, RoomBreakthroughConfig.class);
        } else if ("deathmatch".equalsIgnoreCase(gameplayId)) {
            gameplayConfig = context.deserialize(configElement, RoomDeathmatchConfig.class);
        } else if ("gunmaster".equalsIgnoreCase(gameplayId)) {
            gameplayConfig = context.deserialize(configElement, RoomGunmasterConfig.class);
        } else {
            gameplayConfig = context.deserialize(configElement, RoomConquestConfig.class);
        }
        
        return new RoomDefinition(roomId, displayName, id, state, commonConfig, gameplayConfig);
    }

    @Override
    public JsonElement serialize(RoomDefinition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("version", 15);
        obj.addProperty("roomId", src.roomId());
        obj.addProperty("displayName", src.displayName());
        obj.addProperty("gameplayId", src.gameplayId().value());
        obj.addProperty("lifecycleState", src.lifecycleState().name());
        obj.add("commonConfig", context.serialize(src.commonConfig()));
        obj.add("gameplayConfig", context.serialize(src.gameplayConfig()));
        return obj;
    }
}
