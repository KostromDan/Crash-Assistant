package dev.kostromdan.mods.crash_assistant.common_config.scripts;

import org.apache.commons.jexl3.JexlArithmetic;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlScript;
import org.apache.commons.jexl3.MapContext;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * JexlStreamArithmetic implementation based on Native Java Streams.
 * Delegates operations to java.util.stream.Stream for correctness and performance.
 * <p>
 * <b>PERFORMANCE NOTE:</b>
 * These methods are optimized for <b>readability and convenience</b>.
 * They incur significant overhead due to repeated JEXL-Java context switching (JexlScript.execute()).
 * For large datasets (> 10,000 items) where performance is critical, prefer using standard JEXL <code>for</code> loops.
 * </p>
 */
public class JexlStreamArithmetic extends JexlArithmetic {

    private static final MapContext EMPTY_CONTEXT = new MapContext();

    public JexlStreamArithmetic(boolean strict) {
        super(strict);
    }

    // --- Stream Actions (Lazy, Return Stream) ---

    public Stream<?> filter(Stream<?> stream, JexlScript predicate) {
        JexlContext context = currentContext();
        return stream.filter(item -> toBoolean(predicate.execute(context, item)));
    }

    public Stream<?> map(Stream<?> stream, JexlScript mapper) {
        JexlContext context = currentContext();
        return stream.map(item -> mapper.execute(context, item));
    }

    public Stream<?> flatMap(Stream<?> stream, JexlScript mapper) {
        JexlContext context = currentContext();
        return stream.flatMap(item -> {
            Object mapped = mapper.execute(context, item);
            return toStream(mapped);
        });
    }


    public Stream<?> sorted(Stream<?> stream, JexlScript comparator) {
        JexlContext context = currentContext();
        return stream.sorted((o1, o2) -> {
            Object res = comparator.execute(context, o1, o2);
            return ((Number) res).intValue();
        });
    }


    public Stream<?> peek(Stream<?> stream, JexlScript action) {
        JexlContext context = currentContext();
        return stream.peek(item -> action.execute(context, item));
    }

    // --- Terminal Actions for Streams (Delegates) ---

    public void forEach(Stream<?> stream, JexlScript action) {
        JexlContext context = currentContext();
        stream.forEach(item -> action.execute(context, item));
    }

    public void forEachOrdered(Stream<?> stream, JexlScript action) {
        JexlContext context = currentContext();
        stream.forEachOrdered(item -> action.execute(context, item));
    }

    public boolean anyMatch(Stream<?> stream, JexlScript predicate) {
        JexlContext context = currentContext();
        return stream.anyMatch(item -> toBoolean(predicate.execute(context, item)));
    }

    public boolean allMatch(Stream<?> stream, JexlScript predicate) {
        JexlContext context = currentContext();
        return stream.allMatch(item -> toBoolean(predicate.execute(context, item)));
    }

    public boolean noneMatch(Stream<?> stream, JexlScript predicate) {
        JexlContext context = currentContext();
        return stream.noneMatch(item -> toBoolean(predicate.execute(context, item)));
    }

    public Object reduce(Stream<?> stream, Object identity, JexlScript accumulator) {
        JexlContext context = currentContext();
        return ((Stream<Object>) stream).reduce(identity, (acc, item) -> accumulator.execute(context, acc, item));
    }

    public Object reduce(Stream<?> stream, JexlScript accumulator) {
        JexlContext context = currentContext();
        return ((Stream<Object>) stream).reduce((acc, item) -> accumulator.execute(context, acc, item));
    }

    public Object collect(Stream<?> stream, JexlScript supplier, JexlScript accumulator, JexlScript combiner) {
        JexlContext context = currentContext();
        return ((Stream<Object>) stream).collect(
                () -> supplier.execute(context),
                (acc, item) -> accumulator.execute(context, acc, item),
                (acc1, acc2) -> combiner.execute(context, acc1, acc2)
        );
    }


    public Object min(Stream<?> stream, JexlScript comparator) {
        JexlContext context = currentContext();
        return stream.min((o1, o2) -> {
            Object res = comparator.execute(context, o1, o2);
            return ((Number) res).intValue();
        }).orElse(null);
    }

    public Object max(Stream<?> stream, JexlScript comparator) {
        JexlContext context = currentContext();
        return stream.max((o1, o2) -> {
            Object res = comparator.execute(context, o1, o2);
            return ((Number) res).intValue();
        }).orElse(null);
    }


    // --- Collection Actions (List, Set) ---

    public boolean removeIf(Collection<?> collection, JexlScript filter) {
        JexlContext context = currentContext();
        return collection.removeIf(item -> toBoolean(filter.execute(context, item)));
    }

    public void replaceAll(List<Object> list, JexlScript operator) {
        JexlContext context = currentContext();
        list.replaceAll(item -> operator.execute(context, item));
    }

    public void sort(List<Object> list, JexlScript comparator) {
        JexlContext context = currentContext();
        list.sort((o1, o2) -> {
            Object res = comparator.execute(context, o1, o2);
            return ((Number) res).intValue();
        });
    }

    public void forEach(Iterable<?> iterable, JexlScript action) {
        JexlContext context = currentContext();
        iterable.forEach(item -> action.execute(context, item));
    }

    // --- Map Actions ---

    public void forEach(Map<?, ?> map, JexlScript action) {
        JexlContext context = currentContext();
        map.forEach((k, v) -> action.execute(context, k, v));
    }

    public void replaceAll(Map<Object, Object> map, JexlScript function) {
        JexlContext context = currentContext();
        map.replaceAll((k, v) -> function.execute(context, k, v));
    }

    public Object computeIfAbsent(Map<Object, Object> map, Object key, JexlScript mappingFunction) {
        JexlContext context = currentContext();
        return map.computeIfAbsent(key, k -> mappingFunction.execute(context, k));
    }

    public Object computeIfPresent(Map<Object, Object> map, Object key, JexlScript remappingFunction) {
        JexlContext context = currentContext();
        return map.computeIfPresent(key, (k, v) -> remappingFunction.execute(context, k, v));
    }

    public Object merge(Map<Object, Object> map, Object key, Object value, JexlScript remappingFunction) {
        JexlContext context = currentContext();
        return map.merge(key, value, (v1, v2) -> remappingFunction.execute(context, v1, v2));
    }

    // --- Optional Actions ---

    public void ifPresent(Optional<?> optional, JexlScript consumer) {
        JexlContext context = currentContext();
        optional.ifPresent(val -> consumer.execute(context, val));
    }

    public Optional<?> map(Optional<?> optional, JexlScript mapper) {
        JexlContext context = currentContext();
        return optional.map(val -> mapper.execute(context, val));
    }

    public Optional<?> filter(Optional<?> optional, JexlScript predicate) {
        JexlContext context = currentContext();
        return optional.filter(val -> toBoolean(predicate.execute(context, val)));
    }

    public Object orElseGet(Optional<?> optional, JexlScript supplier) {
        if (optional.isPresent()) {
            return optional.get();
        }
        return supplier.execute(currentContext());
    }

    public Object orElseThrow(Optional<?> optional, JexlScript exceptionSupplier) {
        if (optional.isPresent()) {
            return optional.get();
        }
        Object ex = exceptionSupplier.execute(currentContext());
        if (ex instanceof Throwable) {
            throw new RuntimeException((Throwable) ex);
        }
        throw new RuntimeException(String.valueOf(ex));
    }


    // --- Collectors Shortcuts (GroupBy, ToMap, simplify usage) ---

    public Map<?, List<Object>> groupBy(Stream<?> stream, JexlScript classifier) {
        JexlContext ctx = currentContext();
        // Manual implementation to avoid dealing with Collectors.groupingBy generics issues
        Map<Object, List<Object>> result = new LinkedHashMap<>();
        stream.forEach(item -> {
            Object key = classifier.execute(ctx, item);
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        });
        return result;
    }

    public Map<Boolean, List<Object>> partitioningBy(Stream<?> stream, JexlScript predicate) {
        JexlContext ctx = currentContext();
        Map<Boolean, List<Object>> result = new LinkedHashMap<>();
        result.put(true, new ArrayList<>());
        result.put(false, new ArrayList<>());
        stream.forEach(item -> {
            boolean key = toBoolean(predicate.execute(ctx, item));
            result.get(key).add(item);
        });
        return result;
    }

    public Map<?, ?> toMap(Stream<?> stream, JexlScript keyMapper, JexlScript valueMapper) {
        JexlContext ctx = currentContext();
        Map<Object, Object> result = new LinkedHashMap<>();
        stream.forEach(item -> {
            Object key = keyMapper.execute(ctx, item);
            Object value = Objects.requireNonNull(valueMapper.execute(ctx, item));
            Object old = result.putIfAbsent(key, value);
            if (old != null) {
                throw duplicateKeyException(key, old, value);
            }
        });
        return result;
    }

    public Map<?, ?> toMap(Stream<?> stream, JexlScript keyMapper, JexlScript valueMapper, JexlScript mergeFunction) {
        JexlContext ctx = currentContext();
        Map<Object, Object> result = new LinkedHashMap<>();
        stream.forEach(item -> {
            Object key = keyMapper.execute(ctx, item);
            Object value = Objects.requireNonNull(valueMapper.execute(ctx, item));
            result.merge(key, value, (v1, v2) -> mergeFunction.execute(ctx, v1, v2));
        });
        return result;
    }

    public List<?> toList(Stream<?> stream) {
        List<Object> result = new ArrayList<>();
        stream.forEach(result::add);
        return result;
    }

    public Set<?> toSet(Stream<?> stream) {
        Set<Object> result = new LinkedHashSet<>();
        stream.forEach(result::add);
        return result;
    }

    // --- Helpers ---

    private JexlContext currentContext() {
        JexlContext.ThreadLocal context = JexlEngine.getThreadContext();
        return context != null ? context : EMPTY_CONTEXT;
    }

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

    private IllegalStateException duplicateKeyException(Object key, Object oldValue, Object newValue) {
        return new IllegalStateException("Duplicate key " + key
                + " (attempted merging values " + oldValue + " and " + newValue + ")");
    }
}
