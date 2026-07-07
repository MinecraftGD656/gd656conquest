package org.mods.gd656conquest.server.core.bootstrap;

import org.mods.gd656conquest.server.core.command.ConquestCommandModule;
import org.mods.gd656conquest.server.core.lifecycle.ServerLifecycleCoordinator;
import org.mods.gd656conquest.server.data.DataServerModule;
import org.mods.gd656conquest.server.facility.parkour.ParkourServerModule;
import org.mods.gd656conquest.server.facility.quickaccess.QuickAccessServerModule;
import org.mods.gd656conquest.server.facility.range.RangeServerModule;
import org.mods.gd656conquest.server.facility.sign.SignServerModule;
import org.mods.gd656conquest.server.loadout.LoadoutServerModule;
import org.mods.gd656conquest.server.pack.PackValidationServerModule;
import org.mods.gd656conquest.server.player.PlayerServerModule;
import org.mods.gd656conquest.server.player.state.SleepRuleServerModule;
import org.mods.gd656conquest.server.room.entry.RoomServerModule;
import org.mods.gd656conquest.server.social.party.PartyServerModule;
import org.mods.gd656conquest.server.title.TitleServerModule;
import org.mods.gd656conquest.server.vehicle.VehicleServerModule;

import java.util.List;

public final class ServerFrameworkBootstrap {
    private ServerFrameworkBootstrap() {
    }

    public static ServerLifecycleCoordinator bootstrapDefault() {
        return new ServerLifecycleCoordinator(
            new ServerModuleRegistry(List.of(
                new DataServerModule(),
                new ConquestCommandModule(),
                new PackValidationServerModule(),
                new TitleServerModule(),
                new PartyServerModule(),
                new PlayerServerModule(),
                new SleepRuleServerModule(),
                new QuickAccessServerModule(),
                new ParkourServerModule(),
                new SignServerModule(),
                new LoadoutServerModule(),
                new RangeServerModule(),
                new VehicleServerModule(),
                new RoomServerModule()
            ))
        );
    }
}
