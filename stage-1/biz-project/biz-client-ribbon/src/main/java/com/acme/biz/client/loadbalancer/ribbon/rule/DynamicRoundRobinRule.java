package com.acme.biz.client.loadbalancer.ribbon.rule;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractLoadBalancerRule;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.RoundRobinRule;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ：junsenfu
 * @date ：Created in 2022/11/7 10:39
 * 文件说明： </p>
 */
public class DynamicRoundRobinRule extends AbstractLoadBalancerRule {

    private final Map<String, RoundWeight> weightServer = new ConcurrentHashMap<>();

    //十分钟默认时间
    private long default_warm_up = 10 * 60 * 1000L;

    @Override
    public Server choose(Object key) {
        return choose(getLoadBalancer(), key);
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        //todo 修改热加载时间
    }


    public Server choose(ILoadBalancer lb, Object key) {
        List<Server> allServers = lb.getReachableServers();
        if (allServers.size() == 1) {
            return allServers.get(0);
        }
        //调节
        int total = 0;
        long max = Long.MIN_VALUE;
        Server chooseServer = null;
        RoundWeight chooseWeight = null;
        for (Server server : allServers) {
            if (server instanceof DiscoveryEnabledServer) {
                DiscoveryEnabledServer enabledServer = (DiscoveryEnabledServer) server;
                String name = getName(server);
                long lastUpdated = enabledServer.getInstanceInfo().getLastUpdatedTimestamp();
                RoundWeight weight = weightServer.computeIfAbsent(name, x -> new RoundWeight());
                int new_wight = getWight(lastUpdated);
                //发生变更
                if (new_wight != weight.weight) {
                    weight.setWeight(new_wight);
                }
                //count+weight(步长)
                weight.add();
                long count = weight.getCount();
                if (count > max) {
                    max = count;
                    chooseServer = server;
                    chooseWeight = weight;
                }
                total += new_wight;
            }
        }
        //减少count
        chooseWeight.sub(total);
        return chooseServer;
    }

    public int getWight(long last) {
        long update = System.nanoTime() - last;
        if (update < 0 || update < default_warm_up) {
            return 1;
        }
        int ww = (int) (update - last) / 100;
        return ww;
    }

    private String getName(Server server) {
        String hostPort = server.getHostPort();
        return hostPort + "-" + server.getMetaInfo().getAppName();
    }


    private static class RoundWeight {
        //增长因子
        private int weight = 1;

        private AtomicLong count = new AtomicLong(0);

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public void add() {
            count.addAndGet(weight);
        }

        private long getCount() {
            return count.get();
        }

        //调用后让他的权重编程最小的
        public void sub(int total) {
            count.addAndGet(-1 * total);
        }
    }
}
