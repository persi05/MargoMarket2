package com.margomarket.margomarket.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FavoriteToggleResponse {
    private boolean success;
    private String message;
    private boolean isFavorite;
    private String action;
}