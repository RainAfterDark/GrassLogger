package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;

import java.util.List;

@Command(label = "dmgswitch", aliases = {"ds"}, usage = {""}, permission = "player.unlockall", permissionTargeted = "player.unlockall.others")
public final class DmgSwitchCommand implements CommandHandler {

    public static boolean DealDmg = true;

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        DealDmg = !DealDmg;
        CommandHandler.sendMessage(sender, "Dealing damage " + (DealDmg ? "enabled" : "disabled") + ".");
    }
}