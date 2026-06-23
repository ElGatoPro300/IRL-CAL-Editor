package elgatopro300.cal_lights.client.mixin;

import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockEntityTickInvoker;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(World.class)
public interface WorldBlockEntityTickersAccessor {
    @Accessor("blockEntityTickers")
    List<BlockEntityTickInvoker> irlite$getBlockEntityTickers();
}
