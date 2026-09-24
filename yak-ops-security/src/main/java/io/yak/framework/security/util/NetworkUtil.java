package io.yak.framework.security.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 网络请求工具类。
 *
 * <p>用于获取当前 HTTP 请求及客户端的真实 IP 地址。
 *
 * @author weifuwan
 */
public final class NetworkUtil {

    /**
     * 未知 IP 标识。
     */
    private static final String UNKNOWN = "unknown";

    /**
     * 本机 IPv4 回环地址。
     */
    private static final String LOCAL_IPV4 = "127.0.0.1";

    /**
     * 本机 IPv6 完整回环地址。
     */
    private static final String LOCAL_IPV6 = "0:0:0:0:0:0:0:1";

    /**
     * 本机 IPv6 简写回环地址。
     */
    private static final String LOCAL_IPV6_SHORT = "::1";

    /**
     * 多级代理 IP 分隔符。
     */
    private static final String IP_SEPARATOR = ",";

    /**
     * 从当前请求上下文中获取客户端真实 IP 地址。
     *
     * @return 客户端真实 IP 地址
     * @throws IllegalStateException 当前线程不存在 HTTP 请求上下文时抛出
     */
    public static String getRealIpAddress() {
        RequestAttributes requestAttributes =
                RequestContextHolder.getRequestAttributes();

        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            throw new IllegalStateException("当前线程不存在 HTTP 请求上下文");
        }

        ServletRequestAttributes servletRequestAttributes =
                (ServletRequestAttributes) requestAttributes;

        return NetworkUtil.getRealIpAddress(
                servletRequestAttributes.getRequest());
    }

    /**
     * 判断 IP 地址是否无效。
     *
     * @param ipAddress IP 地址
     * @return IP 地址为空或为 unknown 时返回 true
     */
    private static boolean isNotOk(String ipAddress) {
        return ipAddress == null
                || ipAddress.trim().isEmpty()
                || UNKNOWN.equalsIgnoreCase(ipAddress.trim());
    }

    /**
     * 从指定 HTTP 请求中获取客户端真实 IP 地址。
     *
     * <p>优先从代理请求头中获取，无法获取时使用请求的远程地址。
     *
     * @param request HTTP 请求对象
     * @return 客户端真实 IP 地址
     */
    public static String getRealIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String ipAddress = request.getHeader("X-Forwarded-For");

        if (NetworkUtil.isNotOk(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }

        if (NetworkUtil.isNotOk(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }

        if (NetworkUtil.isNotOk(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }

        ipAddress = NetworkUtil.getFirstValidIpAddress(ipAddress);

        if (NetworkUtil.isLocalIpAddress(ipAddress)) {
            ipAddress = NetworkUtil.getLocalHostAddress(ipAddress);
        }

        return ipAddress;
    }

    /**
     * 获取当前请求的客户端 IP。
     *
     * <p>当调用线程不存在 HTTP 请求上下文时，返回指定默认值，
     * 适用于系统初始化、定时任务和消息消费等场景。</p>
     *
     * @param defaultIpAddress 不存在 HTTP 请求时使用的默认 IP
     * @return 客户端 IP 或默认 IP
     */
    public static String getRealIpAddressOrDefault(
            String defaultIpAddress) {

        RequestAttributes requestAttributes =
                RequestContextHolder.getRequestAttributes();

        if (requestAttributes instanceof ServletRequestAttributes) {
            ServletRequestAttributes servletRequestAttributes =
                    (ServletRequestAttributes) requestAttributes;

            return getRealIpAddress(
                    servletRequestAttributes.getRequest());
        }

        return isNotOk(defaultIpAddress)
                ? LOCAL_IPV4
                : defaultIpAddress.trim();
    }

    /**
     * 从多级代理 IP 地址中获取第一个有效地址。
     *
     * @param ipAddress 原始 IP 地址
     * @return 第一个有效 IP 地址，不存在时返回原始值
     */
    private static String getFirstValidIpAddress(String ipAddress) {
        if (NetworkUtil.isNotOk(ipAddress)) {
            return ipAddress;
        }

        if (!ipAddress.contains(IP_SEPARATOR)) {
            return ipAddress.trim();
        }

        String[] ipAddressArray = ipAddress.split(IP_SEPARATOR);
        for (String currentIpAddress : ipAddressArray) {
            if (!NetworkUtil.isNotOk(currentIpAddress)) {
                return currentIpAddress.trim();
            }
        }

        return ipAddress.trim();
    }

    /**
     * 判断是否为本机回环地址。
     *
     * @param ipAddress IP 地址
     * @return 是本机回环地址时返回 true
     */
    private static boolean isLocalIpAddress(String ipAddress) {
        return LOCAL_IPV4.equals(ipAddress)
                || LOCAL_IPV6.equals(ipAddress)
                || LOCAL_IPV6_SHORT.equals(ipAddress);
    }

    /**
     * 获取本机实际网络地址。
     *
     * @param defaultIpAddress 获取失败时使用的默认地址
     * @return 本机网络地址
     */
    private static String getLocalHostAddress(String defaultIpAddress) {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (UnknownHostException exception) {
            return defaultIpAddress;
        }
    }

    /**
     * 禁止实例化工具类。
     */
    private NetworkUtil() {
        throw new IllegalStateException("Utility class");
    }
}
