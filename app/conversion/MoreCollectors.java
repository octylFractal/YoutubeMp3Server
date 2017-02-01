package conversion;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public final class MoreCollectors {

    public static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
        // ok because the type is just for compile time
        @SuppressWarnings("unchecked")
        ImmutableListCollector<T> recast = (ImmutableListCollector<T>) ImmutableListCollector.INSTANCE;
        return recast;
    }

    private static final class ImmutableListCollector<T> implements Collector<T, ImmutableList.Builder<T>, ImmutableList<T>> {
        private static final ImmutableListCollector<?> INSTANCE = new ImmutableListCollector<>();

        @Override
        public Supplier<ImmutableList.Builder<T>> supplier() {
            return ImmutableList::builder;
        }

        @Override
        public BiConsumer<ImmutableList.Builder<T>, T> accumulator() {
            return ImmutableList.Builder::add;
        }

        @Override
        public BinaryOperator<ImmutableList.Builder<T>> combiner() {
            return (b1, b2) -> b1.addAll(b2.build());
        }

        @Override
        public Function<ImmutableList.Builder<T>, ImmutableList<T>> finisher() {
            return ImmutableList.Builder::build;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return ImmutableSet.of();
        }
    }

    private MoreCollectors() {
    }
}
