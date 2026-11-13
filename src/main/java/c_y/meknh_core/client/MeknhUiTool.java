package c_y.meknh_core.client;

import c_y.meknh_core.MekNHCore;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * 界面编辑工具开关（客户端）。
 *
 * <p>{@code /meknh_ui} 只切换一个本地开关：不拦截右键，也不替换机器界面。
 * 开关打开后，机器界面照常打开，编辑器以覆盖层的形式画在
 * {@code MachineScreen} 上面（见 {@code MachineEditorOverlay}）。</p>
 */
@EventBusSubscriber(modid = MekNHCore.MODID, value = Dist.CLIENT)
public final class MeknhUiTool {

    private static boolean editorMode;

    private MeknhUiTool() {
    }

    public static boolean isEditorMode() {
        return editorMode;
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("meknh_ui").executes(context -> {
            editorMode = !editorMode;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(editorMode
                        ? "gui.meknh_core.ui_editor.on"
                        : "gui.meknh_core.ui_editor.off"), false);
            }
            return 1;
        }));
    }
}
