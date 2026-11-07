package com.margomarket.service;
import com.margomarket.model.User;
public final class PublicNames {
 private PublicNames() {}
 public static String name(User user) {
  String name = user.getDisplayName();
  return name == null || name.isBlank() || name.contains("@") ? "Gracz " + user.getId() : name;
 }
}
