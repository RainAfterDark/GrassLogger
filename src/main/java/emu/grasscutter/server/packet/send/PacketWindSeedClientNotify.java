package emu.grasscutter.server.packet.send;

import com.google.protobuf.ByteString;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.WindSeedClientNotifyOuterClass;

public class PacketWindSeedClientNotify extends BasePacket {

    public PacketWindSeedClientNotify(byte[] areaCode) {
        super(PacketOpcodes.WindSeedClientNotify);

        var proto = WindSeedClientNotifyOuterClass.WindSeedClientNotify.newBuilder();

        proto.setAreaNotify(WindSeedClientNotifyOuterClass
            .WindSeedClientNotify.AreaNotify.newBuilder()
            .setAreaCode(ByteString.copyFrom(areaCode))
            .setAreaType(1)
            .setAreaId(1)
            .build());

        setData(proto.build());
    }
}
