package com.pfe.Controller;

import com.pfe.Dto.PublicSttOptionDTO;
import com.pfe.Service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public")
public class PublicLookupController {

    private final UserService userService;

    public PublicLookupController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/stts")
    public List<PublicSttOptionDTO> stts() {
        return userService.findPublicStts();
    }
}