package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.service.ShoppingCartService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    // 购物车在redis中的key前缀，完整key为 cart:{userId}
    private static final String CART_KEY_PREFIX = "cart:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 生成购物车中单个商品的hash field：
     * 同一道菜品的不同口味视为不同商品，套餐之间按套餐id区分
     */
    private String buildField(ShoppingCart shoppingCart) {
        if (shoppingCart.getDishId() != null) {
            String flavor = shoppingCart.getDishFlavor() == null ? "" : shoppingCart.getDishFlavor();
            return "dish:" + shoppingCart.getDishId() + ":" + flavor;
        }
        return "setmeal:" + shoppingCart.getSetmealId();
    }

    private String currentCartKey() {
        return CART_KEY_PREFIX + BaseContext.getCurrentId();
    }

    /**
     * 添加购物车
     *
     * @param shoppingCartDTO
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        Long userId = BaseContext.getCurrentId();
        String key = CART_KEY_PREFIX + userId;
        String field = (shoppingCartDTO.getDishId() != null)
                ? "dish:" + shoppingCartDTO.getDishId() + ":" + (shoppingCartDTO.getDishFlavor() == null ? "" : shoppingCartDTO.getDishFlavor())
                : "setmeal:" + shoppingCartDTO.getSetmealId();

        // 判断该商品是否已经在购物车中
        ShoppingCart cart = (ShoppingCart) redisTemplate.opsForHash().get(key, field);

        // 如果已经存在，将数量+1
        if (cart != null) {
            cart.setNumber(cart.getNumber() + 1);
            redisTemplate.opsForHash().put(key, field, cart);
            return;
        }

        // 不存在，补全商品信息后放入购物车，数量为1
        cart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, cart);
        cart.setUserId(userId);
        cart.setNumber(1);
        cart.setCreateTime(LocalDateTime.now());

        Long dishId = shoppingCartDTO.getDishId();
        if (dishId != null) {
            // 添加菜品
            Dish dish = dishMapper.getById(dishId);
            cart.setName(dish.getName());
            cart.setImage(dish.getImage());
            cart.setAmount(dish.getPrice());
            cart.setDishFlavor(shoppingCartDTO.getDishFlavor());
        } else {
            // 添加套餐
            Setmeal setmeal = setmealMapper.getById(shoppingCartDTO.getSetmealId());
            cart.setName(setmeal.getName());
            cart.setImage(setmeal.getImage());
            cart.setAmount(setmeal.getPrice());
        }
        redisTemplate.opsForHash().put(key, field, cart);
    }

    /**
     * 查看购物车（按加入时间升序返回）
     *
     * @return
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(currentCartKey());
        List<ShoppingCart> list = new ArrayList<>();
        entries.values().forEach(item -> list.add((ShoppingCart) item));
        list.sort(Comparator.comparing(ShoppingCart::getCreateTime,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        return list;
    }

    /**
     * 清空购物车
     */
    @Override
    public void cleanShoppingCart() {
        redisTemplate.delete(currentCartKey());
    }

    /**
     * 删除购物车中一个商品：数量为1时移除该项，否则数量-1
     *
     * @param shoppingCartDTO
     */
    @Override
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart param = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, param);
        String key = currentCartKey();
        String field = buildField(param);

        ShoppingCart cart = (ShoppingCart) redisTemplate.opsForHash().get(key, field);
        if (cart == null) {
            return;
        }
        if (cart.getNumber() == 1) {
            redisTemplate.opsForHash().delete(key, field);
        } else {
            cart.setNumber(cart.getNumber() - 1);
            redisTemplate.opsForHash().put(key, field, cart);
        }
    }

    /**
     * 再来一单：将订单明细批量加入购物车，已存在的同款商品数量累加
     *
     * @param shoppingCartList 由订单明细转换来的购物车对象
     */
    @Override
    public void addFromOrderDetails(List<ShoppingCart> shoppingCartList) {
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            return;
        }
        String key = currentCartKey();
        for (ShoppingCart item : shoppingCartList) {
            String field = buildField(item);
            ShoppingCart exist = (ShoppingCart) redisTemplate.opsForHash().get(key, field);
            if (exist != null) {
                exist.setNumber(exist.getNumber() + item.getNumber());
                redisTemplate.opsForHash().put(key, field, exist);
            } else {
                item.setId(null);
                redisTemplate.opsForHash().put(key, field, item);
            }
        }
    }
}
