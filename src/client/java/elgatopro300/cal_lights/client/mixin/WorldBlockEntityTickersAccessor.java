package elgatopro300.cal_lights.client.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Level.class)
public interface WorldBlockEntityTickersAccessor {
    @Accessor("blockEntityTickers")
    List<TickingBlockEntity> irlite$getBlockEntityTickers();
}
