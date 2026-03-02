package com.example.nagoyameshi.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.nagoyameshi.entity.Shop;
import com.example.nagoyameshi.form.ReservationInputForm;
import com.example.nagoyameshi.repository.ShopRepository;

@Controller
@RequestMapping("/shops")
public class ShopController {
    private final ShopRepository shopRepository;        
    
    public ShopController(ShopRepository shopRepository) {
        this.shopRepository = shopRepository;            
    }     
  
    @GetMapping
    
    public String index(@PageableDefault(page = 0, size = 10, sort = "id", direction = Direction.ASC) Pageable pageable,Model model)
    /*
    public String index(@RequestParam(name = "keyword", required = false) String keyword,
                        @RequestParam(name = "area", required = false) String area,
                        @RequestParam(name = "price", required = false) Integer price,                        
                        @PageableDefault(page = 0, size = 10, sort = "id", direction = Direction.ASC) Pageable pageable,
                        Model model) 
                        */
    {
        Page<Shop> shopPage;
        
        shopPage = shopRepository.findAll(pageable);
        
        /*
        if (keyword != null && !keyword.isEmpty()) {
        	shopPage = shopRepository.findByNameLikeOrAddressLike("%" + keyword + "%", "%" + keyword + "%", pageable);
        } else if (area != null && !area.isEmpty()) {
        	shopPage = shopRepository.findByAddressLike("%" + area + "%", pageable);
        } else if (price != null) {
        	shopPage = shopRepository.findByPriceLessThanEqual(price, pageable);
        } else {
        	shopPage = shopRepository.findAll(pageable);
        }                
        
        */
        model.addAttribute("shopPage",shopPage);

        return "shops/index";
    }
    
    @GetMapping("/{id}")
    public String show(@PathVariable(name = "id") Integer id,Model model) {
    	Shop shop = shopRepository.getReferenceById(id);
    	
    	model.addAttribute("shop",shop);
    	model.addAttribute("reservationInputForm",new ReservationInputForm());
    	
    	return "shops/show";
    }
    
}