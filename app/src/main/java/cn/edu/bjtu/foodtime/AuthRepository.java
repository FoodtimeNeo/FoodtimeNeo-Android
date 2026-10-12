package cn.edu.bjtu.foodtime;
import cn.edu.bjtu.foodtime.Models.User;
public interface AuthRepository {
    User currentUser();
    User login(String account,String password);
    String sendVerificationCode(String email);
    void register(String email,String password,String code);
    void logout();
}
