package com.margomarket.margomarket.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserStats {
    private long totalListings;
    private long activeListings;
    private long soldListings;
}