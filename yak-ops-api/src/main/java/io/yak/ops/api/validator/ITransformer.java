package io.yak.ops.api.validator;

public interface ITransformer<T, R> {

    R transform(T t);

}
