package com.datafabric.dataservice.exception;

/** 客户不存在（Cube 返回 0 行） */
public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String custId) {
        super("Customer not found: " + custId);
    }
}
