package top.contins.authservice.model.common;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListResult<T> {

    private long total;
    private List<T> data;
}