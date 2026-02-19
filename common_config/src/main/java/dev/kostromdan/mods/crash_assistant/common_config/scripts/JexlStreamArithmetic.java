package dev.kostromdan.mods.crash_assistant.common_config.scripts;

import org.apache.commons.jexl3.JexlArithmetic;
import org.apache.commons.jexl3.JexlScript;
import org.apache.commons.jexl3.MapContext;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * JexlStreamArithmetic implementation based on Native Java Streams.
 * Delegates operations to java.util.stream.Stream for correctness and performance.
 */
public class JexlStreamArithmetic extends JexlArithmetic {

    public JexlStreamArithmetic(boolean strict) {
        super(strict);
    }

    // --- Stream Actions (Lazy, Return Stream) ---

    public Stream<?> filter(Stream<?> stream, JexlScript predicate) {
        MapContext ctx = new MapContext();
        return stream.filter(item -> toBoolean(predicate.execute(ctx, item)));
    }

    public Stream<?> map(Stream<?> stream, JexlScript mapper) {
        MapContext ctx = new MapContext();
        return stream.map(item -> mapper.execute(ctx, item));
    }

    public Stream<?> flatMap(Stream<?> stream, JexlScript mapper) {
        MapContext ctx = new MapContext();
        return stream.flatMap(item -> {
            Object mapped = mapper.execute(ctx, item);
            return toStream(mapped);
        });
    }


    public Stream<?> sorted(Stream<?> stream, JexlScript comparator) {
        MapContext ctx = new MapContext();
        return stream.sorted((o1, o2) -> {
            Object res = comparator.execute(ctx, o1, o2);
            if (res instanceof Number) {
                return ((Number) res).intValue();
            }
            return 0;
        });
    }


    public Stream<?> peek(Stream<?> stream, JexlScript action) {
        MapContext ctx = new MapContext();
        return stream.peek(item -> action.execute(ctx, item));
    }

    // --- Terminal Actions for Streams (Delegates) ---

    public void forEach(Stream<?> stream, JexlScript action) {
        MapContext ctx = new MapContext();
        stream.forEach(item -> action.execute(ctx, item));
    }

    public void forEachOrdered(Stream<?> stream, JexlScript action) {
        MapContext ctx = new MapContext();
        stream.forEachOrdered(item -> action.execute(ctx, item));
    }

    public boolean anyMatch(Stream<?> stream, JexlScript predicate) {
        MapContext ctx = new MapContext();
        return stream.anyMatch(item -> toBoolean(predicate.execute(ctx, item)));
    }

    public boolean allMatch(Stream<?> stream, JexlScript predicate) {
        MapContext ctx = new MapContext();
        return stream.allMatch(item -> toBoolean(predicate.execute(ctx, item)));
    }

    public boolean noneMatch(Stream<?> stream, JexlScript predicate) {
        MapContext ctx = new MapContext();
        return stream.noneMatch(item -> toBoolean(predicate.execute(ctx, item)));
    }

    public Object reduce(Stream<?> stream, Object identity, JexlScript accumulator) {
        MapContext ctx = new MapContext();
        return ((Stream<Object>) stream).reduce(identity, (acc, item) -> accumulator.execute(ctx, acc, item));
    }

    public Object reduce(Stream<?> stream, JexlScript accumulator) {
        MapContext ctx = new MapContext();
        return ((Stream<Object>) stream).reduce((acc, item) -> accumulator.execute(ctx, acc, item));
    }

    public Object collect(Stream<?> stream, JexlScript supplier, JexlScript accumulator, JexlScript combiner) {
        MapContext ctx = new MapContext();
        return ((Stream<Object>) stream).collect(
                () -> supplier.execute(ctx),
                (acc, item) -> accumulator.execute(ctx, acc, item),
                (acc1, acc2) -> combiner.execute(ctx, acc1, acc2)
        );
    }


    public Object min(Stream<?> stream, JexlScript comparator) {
        MapContext ctx = new MapContext();
        return stream.min((o1, o2) -> {
            Object res = comparator.execute(ctx, o1, o2);
            if (res instanceof Number) {
                return ((Number) res).intValue();
            }
            return 0; // fallback
        }).orElse(null);
    }

    public Object max(Stream<?> stream, JexlScript comparator) {
        MapContext ctx = new MapContext();
        return stream.max((o1, o2) -> {
            Object res = comparator.execute(ctx, o1, o2);
            if (res instanceof Number) {
                return ((Number) res).intValue();
            }
            return 0; // fallback
        }).orElse(null);
    }


    // --- Helpers ---

    /**
     * Helper to convert JEXL result to Stream if needed for flatMap internal logic only.
     */
    private Stream<Object> toStream(Object object) {
        if (object == null) {
            return Stream.empty();
        }
        if (object instanceof Stream) {
            return (Stream<Object>) object;
        }
        if (object instanceof Collection) {
            return ((Collection<Object>) object).stream();
        }
        if (object.getClass().isArray()) {
            Object[] objArray;
            if (object instanceof Object[]) {
                objArray = (Object[]) object;
            } else {
                int len = java.lang.reflect.Array.getLength(object);
                objArray = new Object[len];
                for (int i = 0; i < len; i++) objArray[i] = java.lang.reflect.Array.get(object, i);
            }
            return Arrays.stream(objArray);
        }
        if (object instanceof Iterable) {
            return StreamSupport.stream(((Iterable<Object>) object).spliterator(), false);
        }
        if (object instanceof Iterator) {
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize((Iterator<Object>) object, Spliterator.ORDERED), false);
        }
        return Stream.of(object);
    }
}
