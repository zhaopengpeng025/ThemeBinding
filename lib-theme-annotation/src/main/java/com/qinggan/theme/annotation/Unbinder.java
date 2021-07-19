package com.qinggan.theme.annotation;

public interface Unbinder {
  void unbind();

  Unbinder EMPTY = () -> { };
}
