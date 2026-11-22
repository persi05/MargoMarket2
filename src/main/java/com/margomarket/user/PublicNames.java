package com.margomarket.user;
public final class PublicNames {
 private PublicNames() {}
 public static String name(User user) {
  String name = user.getDisplayName();
  return name == null || name.isBlank() || name.contains("@") ? "Gracz " + user.getId() : name;
 }
}
