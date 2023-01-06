package tech.pegasys.heku.util.net.libp2p.proxy

import io.netty.channel.ChannelHandler
import io.netty.handler.proxy.Socks5ProxyHandler
import java.net.SocketAddress

/*

    Installing remote IP with Socks5 proxy on Ubuntu:

echo 1 > /proc/sys/net/ipv4/ip_forward

export DEST_IP=188.134.70.1
export LOCAL_IP=194.233.163.169

iptables -F
iptables -t nat -F
iptables -X
iptables -t nat -A PREROUTING -p tcp --dport 9000:10000 -j DNAT --to $DEST_IP
iptables -t nat -A POSTROUTING -p tcp -d $DEST_IP --dport 9000:10000 -j SNAT --to-source $LOCAL_IP
iptables -t nat -A PREROUTING -p udp --dport 9000:10000 -j DNAT --to $DEST_IP
iptables -t nat -A POSTROUTING -p udp -d $DEST_IP --dport 9000:10000 -j SNAT --to-source $LOCAL_IP

apt-get install iptables-persistent
netfilter-persistent save
netfilter-persistent reload


apt install dante-server
useradd socks
passwd socks #proxy79

echo "logoutput: syslog"  > /etc/danted.conf
echo "user.privileged: root" >> /etc/danted.conf
echo "user.unprivileged: nobody" >> /etc/danted.conf
echo "internal: 0.0.0.0 port=1080" >> /etc/danted.conf
echo "external: eth0" >> /etc/danted.conf
echo "socksmethod: username" >> /etc/danted.conf
echo "clientmethod: none" >> /etc/danted.conf
echo "client pass {" >> /etc/danted.conf
echo "        from: 0.0.0.0/0 to: 0.0.0.0/0" >> /etc/danted.conf
echo "        log: connect disconnect error" >> /etc/danted.conf
echo "}" >> /etc/danted.conf
echo "socks pass {" >> /etc/danted.conf
echo "        from: 0.0.0.0/0 to: 0.0.0.0/0" >> /etc/danted.conf
echo "        log: connect disconnect error" >> /etc/danted.conf
echo "}" >> /etc/danted.conf
systemctl enable danted
systemctl start danted
systemctl status danted.service

 */
class Socks5Proxy(
    private val socks5addr: SocketAddress,
    private val user: String?,
    private val pass: String?
) : Proxy() {

    init {
        require((user == null && pass == null) || (user != null && pass != null))
    }

    override fun createProxyNettyHandler(): ChannelHandler {
        return (
                (if (user == null)
                    Socks5ProxyHandler(socks5addr)
                else
                    Socks5ProxyHandler(socks5addr, user, pass))
                )
            .also {
                it.setConnectTimeoutMillis(0)
            }
    }

    override fun toString() = "Socks5Proxy[$socks5addr]"
}