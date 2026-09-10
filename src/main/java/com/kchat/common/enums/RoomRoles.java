package com.kchat.common.enums;

public final class RoomRoles {

  public static final String OWNER = "owner";
  public static final String ADMIN = "admin";
  public static final String MEMBER = "member";

  private RoomRoles() {}

  public static boolean isManager(String role) {
    return OWNER.equals(role) || ADMIN.equals(role);
  }
}
