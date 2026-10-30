package com.nexo.app.shared.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Paged implements Serializable {
    private int size;
    private int number;
    private long totalElements;
    private int totalPages;
}
