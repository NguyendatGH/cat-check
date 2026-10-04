package com.catcheck.credit.application;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link ObjectProvider} bọc đúng một giá trị — hoặc {@code null} để diễn tả "chưa có bean nào".
 *
 * <p>Cần vì mọi method của {@code ObjectProvider} đều là {@code default}, nên nó không phải
 * functional interface và không viết được bằng lambda.</p>
 */
final class SingletonObjectProvider<T> implements ObjectProvider<T> {

    private final T value;

    SingletonObjectProvider(T value) {
        this.value = value;
    }

    @Override
    public T getObject() {
        if (value == null) {
            throw new NoSuchBeanDefinitionException("khong co bean nao duoc dang ky");
        }
        return value;
    }

    @Override
    public T getObject(Object... args) {
        return getObject();
    }

    @Override
    public T getIfAvailable() {
        return value;
    }

    @Override
    public T getIfUnique() {
        return value;
    }
}
