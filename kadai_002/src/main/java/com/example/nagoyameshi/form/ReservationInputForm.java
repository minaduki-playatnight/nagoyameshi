package com.example.nagoyameshi.form;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Data;


@Data
public class ReservationInputForm {

    @NotNull(message = "予約日時を選択してください。")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime startAt;

    @NotNull(message = "予約人数を入力してください。")
    @Min(value = 1, message = "予約人数は1人以上に設定してください。")
    @Max(value = 10, message = "予約上限人数を超えています。")
    private Integer partySize;
}
