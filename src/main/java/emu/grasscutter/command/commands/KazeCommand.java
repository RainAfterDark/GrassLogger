package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.server.packet.send.PacketWindSeedClientNotify;

import java.util.List;

@Command(label = "kaze", aliases = {"kz"}, usage = {"<blessing>"}, permission = "player.unlockall", permissionTargeted = "player.unlockall.others")
public final class KazeCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.size() < 1) {
            CommandHandler.sendMessage(sender, "No 'blessing' provided.");
            return;
        }

        byte[] areaCode = FileUtils.read(FileUtils.getDataPath("lua/" + args.get(0) + ".luac"));
        if (areaCode.length > 0) {
            sender.getSession().send(new PacketWindSeedClientNotify(areaCode));
            CommandHandler.sendMessage(sender, "The Anemo Archon has blessed you.");
        } else CommandHandler.sendMessage(sender, "The Anemo Archon could not bless you.");
    }
}