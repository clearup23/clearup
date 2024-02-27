package com.example.demo.controller;

import com.example.demo.Configs.Smtp_Mail;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;


import com.example.demo.service.UserServiceImpl;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONObject;
import java.security.SecureRandom;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

@RestController
//@CrossOrigin("http://localhost:3000")
public class UserController {

    private static String CHAT_ENGINE_PRIVATE_KEY = "d7d25660-280c-462e-a9ec-87d0d9892b84";
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private Smtp_Mail smtpMail;

    private final Map<String, String> emailToOtpMap = new HashMap<>();

    User newuser(User newUser){
        System.out.println(newUser);
        return userRepository.save(newUser);
    }
    @PostMapping("/updatePassword")
    public ResponseEntity<String> updatePassword(@RequestBody Map<String, String> requestBody) {
        try {
            String email = requestBody.get("email");
            String currentPassword = requestBody.get("currentPassword");
            String newPassword = requestBody.get("newPassword");

            User user = userService.getByUserEmail(email);

            if (user != null && user.getPassword().equals(currentPassword)) {
                user.setPassword(newPassword);
                userService.saveorUpdateUser(user);

                return ResponseEntity.ok("Password updated successfully");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid email or current password");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
        }
    }


    @PostMapping("/user")
    public ResponseEntity<?> signUp(@RequestBody HashMap<String, String> request) {
        String username = request.get("name");
        String secret = request.get("password");
        String email = request.get("email");
        String firstName = request.get("name");
        String lastName = request.get("name");
        String role= request.get("role");
        String subject = request.get("subject");
        String document = request.get("document");

        System.out.println(username);
        System.out.println(secret);System.out.println(email);System.out.println(role);
        User existingUser = userRepository.findByEmail(email);
        if (existingUser != null) {
            return ResponseEntity.badRequest().body("Email already registered");
        }

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setDocument(document);
        newUser.setRole(role);
        newUser.setName(username);
        newUser.setPassword(secret);
        newUser.setSubject(subject);
        newuser(newUser);

        try {
            URL url = new URL("https://api.chatengine.io/users");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("Private-Key", CHAT_ENGINE_PRIVATE_KEY);
            con.setDoOutput(true);
            Map<String, String> body = new HashMap<>();
            body.put("username", username);
            body.put("secret", secret);
            body.put("email", email);
            body.put("first_name", firstName);
            body.put("last_name", lastName);
            String jsonInputString = new JSONObject(body).toString();
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            StringBuilder responseStr = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), "utf-8"))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    responseStr.append(responseLine.trim());
                }
            }
            Map<String, Object> response = new Gson().fromJson(
                    responseStr.toString(), new TypeToken<HashMap<String, Object>>() {}.getType());
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/users")
    List<User> getAllUsers(){
        return userRepository.findAll();
    }
    @GetMapping("/userSignIn")
    public ResponseEntity<?> checkUserSignIn(@RequestParam(value = "email") String userEmail,@RequestParam(value = "password") String userPassword){
        Optional<User> user=userRepository.findOptionalByEmail(userEmail);
        if(user.isPresent()){
            if(user.get().getPassword().equals(userPassword))
            {
                return new ResponseEntity<>(user.get(), HttpStatus.OK);
            }
            else{
                return new ResponseEntity<>("Wrong Password",HttpStatus.CONFLICT);
            }
        }else{
            return new ResponseEntity<>("User email not found.",HttpStatus.NOT_FOUND);
        }
    }
    @DeleteMapping("/user/{userid}")
    String deleteUser(@PathVariable Integer userid){
        if(!userRepository.existsById(userid)){
            throw new UserNotFoundException(userid);
        }
        userRepository.deleteById(userid);
        return "User with Id "+userid+"has been deleted";
    }

//new code
@GetMapping("/admin/count")
public ResponseEntity<?> countAdmins() {
    long count = userRepository.countByRole("admin");
    return ResponseEntity.ok().body(count);
}


    //new code

    @GetMapping("/checkEmailExists/{email}")
    public ResponseEntity<Map<String, Boolean>> checkEmailExists(@PathVariable String email) {
        try {
            User user = userRepository.findByEmail(email);
            Map<String, Boolean> response = new HashMap<>();
            response.put("exists", user != null);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Boolean> response = new HashMap<>();
            response.put("exists", false);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }


    @GetMapping("/otp/{email}")
    public ResponseEntity<String> sendEmail(@PathVariable String email) {
        try {
            String generatedOTP = smtpMail.sendOTPService(email);
            emailToOtpMap.put(email, generatedOTP);
            return ResponseEntity.ok("OTP sent successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
        }
    }

    @PostMapping("/verifyOtp/{email}")
    public ResponseEntity<String> verifyOtp(@PathVariable String email, @RequestBody Map<String, String> requestBody) {
        try {
            User user = userService.getByUserEmail(email);

            if (user != null) {
                String storedOtp = emailToOtpMap.get(email);
                System.out.println("Stored OTP: " + storedOtp);
                System.out.println("Received OTP: " + requestBody.get("otp"));
                if (storedOtp != null && storedOtp.equals(requestBody.get("otp"))) {
                    String newPassword = generateRandomPassword();
                    smtpMail.sendNewPasswordService(email, newPassword);
                    user.setPassword(newPassword);
                    userService.saveorUpdateUser(user);
                    emailToOtpMap.remove(email);
                    return ResponseEntity.ok("Password updated successfully");
                } else {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid OTP");
                }
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User with this email does not exist");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
        }
    }

    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+";
        StringBuilder newPassword = new StringBuilder(12);
        for (int i = 0; i < 6; i++) {
            newPassword.append(characters.charAt(random.nextInt(characters.length())));
        }
        return newPassword.toString();
    }
}

class OTPVerificationRequest {
    private String email;
    private String otp;
    private Integer userid;

    public OTPVerificationRequest(Integer userid) {
        super();
        this.userid = userid;
    }

    public Integer getUserid() {
        return userid;
    }

    public void setUserid(Integer userid) {
        this.userid = userid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    @Override
    public String toString() {
        return "OTPVerificationRequest [userid =" + userid + "email=" + email + ", otp=" + otp + "]";
    }

    public OTPVerificationRequest(String email, String otp) {
        super();
        this.email = email;
        this.otp = otp;
    }

    public OTPVerificationRequest() {
        super();
    }
}