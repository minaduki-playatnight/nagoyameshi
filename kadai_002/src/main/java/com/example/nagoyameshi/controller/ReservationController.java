package com.example.nagoyameshi.controller;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.nagoyameshi.Security.UserDetailsImpl;
import com.example.nagoyameshi.entity.Reservation;
import com.example.nagoyameshi.entity.Shop;
import com.example.nagoyameshi.entity.User;
import com.example.nagoyameshi.form.ReservationInputForm;
import com.example.nagoyameshi.form.ReservationRegisterForm;
import com.example.nagoyameshi.repository.ReservationRepository;
import com.example.nagoyameshi.repository.ShopRepository;
import com.example.nagoyameshi.service.ReservationService;


@Controller
public class ReservationController {
	private final ReservationRepository reservationRepository;
	private final ShopRepository shopRepository;
	private final ReservationService reservationService;
	
	public ReservationController(ReservationRepository reservationRepository,ShopRepository shopRepository,ReservationService reservationService) {
		this.reservationRepository = reservationRepository;  
		this.shopRepository = shopRepository;
		this.reservationService = reservationService;
	}

	@GetMapping("/reservations")
	public String index(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl, @PageableDefault(page = 0, size = 10, sort = "id", direction = Direction.ASC) Pageable pageable, Model model) {
	User user = userDetailsImpl.getUser();
	Page<Reservation> reservationPage = reservationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
	
	model.addAttribute("reservationPage",reservationPage);
	
	return "reservations/index";
	}
	
	@GetMapping("/shops/{id}/reservations/input")
	public String input(@PathVariable(name = "id") Integer id,
			@ModelAttribute @Validated ReservationInputForm reservationInputForm,
			BindingResult bindingResult,
			RedirectAttributes redirectAttributes,
			Model model)
	{
		Shop shop = shopRepository.getReferenceById(id);
		Integer partySize = reservationInputForm.getPartySize();
		Integer capacity = shop.getCapacity();
		
		if(partySize != null) {
			if(!reservationService.isWithinCapacity(partySize, capacity)) {
				FieldError fieldError = new FieldError(bindingResult.getObjectName(),"partySize","予約人数が定員を超えています。");
				bindingResult.addError(fieldError);
			}
		}
		
		if(bindingResult.hasErrors()) {
			model.addAttribute("shop",shop);
			model.addAttribute("errorMessage","予約内容に不備があります");
			return "shops/show";
		}
		
		redirectAttributes.addFlashAttribute("reservationInputForm",reservationInputForm);
		
		return "redirect:/shops/{id}/reservations/confirm";
		
	}
	
    @GetMapping("/shops/{id}/reservations/confirm")
    public String confirm(@PathVariable(name = "id") Integer id,
                          @ModelAttribute ReservationInputForm reservationInputForm,
                          @AuthenticationPrincipal UserDetailsImpl userDetailsImpl,                          
                          Model model) 
    {        
        Shop shop = shopRepository.getReferenceById(id);
        User user = userDetailsImpl.getUser(); 
                
        //予約時刻と終了時刻を調整
        LocalDateTime startAt = reservationInputForm.getStartAt();
        LocalDateTime endAt = startAt.plusMinutes(90);
 
        
        ReservationRegisterForm reservationRegisterForm = new ReservationRegisterForm(shop.getId(), user.getId(),startAt,endAt, reservationInputForm.getPartySize());
        
        model.addAttribute("shop", shop);  
        model.addAttribute("reservationRegisterForm", reservationRegisterForm);       
        
        return "reservations/confirm";
    }    
    
    @PostMapping("/shops/{id}/reservations/create")
    public String create(@ModelAttribute ReservationRegisterForm reservationRegisterForm) {                
        reservationService.create(reservationRegisterForm);        
        
        return "redirect:/reservations?reserved";
    }

    

}
