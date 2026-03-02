package com.example.nagoyameshi.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nagoyameshi.entity.Reservation;
import com.example.nagoyameshi.entity.Shop;
import com.example.nagoyameshi.entity.User;
import com.example.nagoyameshi.form.ReservationRegisterForm;
import com.example.nagoyameshi.repository.ReservationRepository;
import com.example.nagoyameshi.repository.ShopRepository;
import com.example.nagoyameshi.repository.UserRepository;


@Service
public class ReservationService {
	
    private final ReservationRepository reservationRepository;  
    private final ShopRepository shopRepository;  
    private final UserRepository userRepository;  
    
    public ReservationService(ReservationRepository reservationRepository, ShopRepository shopRepository, UserRepository userRepository) {
        this.reservationRepository = reservationRepository;  
        this.shopRepository = shopRepository;  
        this.userRepository = userRepository;  
    }    
    
    @Transactional
    public void create(ReservationRegisterForm reservationRegisterForm) { 
        Reservation reservation = new Reservation();
        Shop shop = shopRepository.getReferenceById(reservationRegisterForm.getShop());
        User user = userRepository.getReferenceById(reservationRegisterForm.getUser());
        LocalDateTime startAt = reservationRegisterForm.getStartAt();
        LocalDateTime endAt = reservationRegisterForm.getEndAt();         
                
        reservation.setShop(shop);
        reservation.setUser(user);
        reservation.setStartAt(startAt);
        reservation.setEndAt(endAt);
        reservation.setPartySize(reservationRegisterForm.getPartySize());
        
        reservationRepository.save(reservation);
    }  
	
	
	   // 予約人数が定員以下かどうかをチェックする
    public boolean isWithinCapacity(Integer partySize, Integer capacity) {
        return partySize <= capacity;
    }
    

    
    
}
