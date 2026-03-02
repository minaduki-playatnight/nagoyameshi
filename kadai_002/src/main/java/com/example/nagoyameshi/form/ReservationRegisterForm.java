package com.example.nagoyameshi.form;


import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReservationRegisterForm {
    private Integer shop;
    
    private Integer user;    
        
    private LocalDateTime startAt;   
        
    private LocalDateTime endAt;   
    
    private Integer partySize;
    
}
