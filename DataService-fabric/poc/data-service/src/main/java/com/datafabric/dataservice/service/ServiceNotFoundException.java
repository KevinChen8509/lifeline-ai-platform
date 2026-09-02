package com.datafabric.dataservice.service;

/** W5：服务不存在（注册表查无此 slug）→ 404 */
public class ServiceNotFoundException extends RuntimeException {
    public ServiceNotFoundException(String slug) {
        super("服务不存在: " + slug);
    }
}
