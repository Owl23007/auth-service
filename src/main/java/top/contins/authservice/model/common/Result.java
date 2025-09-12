package top.contins.authservice.model.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 封装简单API响应结果的实体类。
 *
 * @param <T> 返回数据的具体类型
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Result<T> {
    /**
     * 状态码：0表示成功，其他值表示不同的错误情况。
     */
    private Integer code;
    /**
     * 提示信息，描述操作的结果或错误原因。
     */
    private String message;
    /**
     * 返回的数据，可以是任意类型。
     */
    private T data;

    /**
     * 成功响应，不带数据。
     *
     * @return 包含成功状态的Result对象
     */
    public static <T> Result<T> success() {
        return new Result<>(0, "success", null);
    }

    /**
     * 成功响应，带有数据。
     *
     * @param data 返回的数据
     * @param <E>  数据类型
     * @return 包含成功状态和数据的Result对象
     */
    public static <E> Result<E> success(E data) {
        return new Result<>(0, "success", data);
    }

    /**
     * 成功响应，带有自定义消息和数据。
     *
     * @param message 自定义的成功消息
     * @param data 返回的数据
     */
    public static <E> Result<E> success(String message, E data) {
        return new Result<>(0, message, data);
    }

    /**
     * 错误响应，包含自定义的消息。
     *
     * @param message 错误消息
     * @return 包含错误状态和消息的Result对象
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(-1, message, null);
    }

    /**
     * 自定义状态码的响应。
     *
     * @param code    状态码
     * @param message 提示信息
     * @param data    返回的数据
     * @param <E>     数据类型
     * @return 包含指定状态码、消息和数据的Result对象
     */
    public static <E> Result<E> custom(Integer code, String message, E data) {
        return new Result<>(code, message, data);
    }
}