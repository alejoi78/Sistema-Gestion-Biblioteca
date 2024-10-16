package com.example.demo.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import io.jsonwebtoken.SignatureException;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import java.util.List;
import java.util.Optional;

import com.example.demo.AuthRequest;
import com.example.demo.UserRegistrationDetails;
import com.example.demo.mail.EmailDetails;
import com.example.demo.mail.EmailService;
import com.example.demo.models.Roles;
import com.example.demo.models.Usuarios;
import com.example.demo.repositories.RolRepository;
import com.example.demo.repositories.UsuariosRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Controller
public class ControladorREST {

    @Autowired
    private UsuariosRepository usuariosRepository; // Cambia a minúsculas consistentemente

    @Autowired
    private RolRepository rolRepository; // Cambia a minúsculas consistentemente

    @Autowired
    private PasswordEncoder passwordEncoder; // Solo usa la inyección aquí

    @Autowired
    private EmailService emailService;

    // Clave secreta para firmar el JWT
    private static final SecretKey SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    // Página de inicio
    @GetMapping("/")
    public String comienzo() {
        return "vistaIndice";
    }

    // Página del menú
    @GetMapping("/vistamenu")
    public String vistaMenu() {
        return "vistamenu";
    }

    // Página de registrar
    @GetMapping("/registro")
    public String registro() {
        return "registro";
    }

    // Recuperar contraseña
    @GetMapping("/recuperarcontraseña")
    public String recuperarcontraseña() {
        return "recuperarcontraseña";
    }

    // Método para obtener todos los usuarios (HTTP GET)
    @GetMapping("/usuarios")
    @ResponseBody
    public ResponseEntity<List<Usuarios>> getUsuarios() {
        List<Usuarios> usuarios = usuariosRepository.findAll();
        return new ResponseEntity<>(usuarios, HttpStatus.OK);
    }

    // Método para agregar un nuevo usuario (HTTP POST)
    @PostMapping("/usuarios")
    @ResponseBody
    public ResponseEntity<Usuarios> agregarUsuario(@RequestBody Usuarios nuevoUsuario) {
        nuevoUsuario.setPassword(passwordEncoder.encode(nuevoUsuario.getPassword())); // Encriptar la contraseña
        Usuarios usuarioGuardado = usuariosRepository.save(nuevoUsuario);
        return new ResponseEntity<>(usuarioGuardado, HttpStatus.CREATED);
    }

    // Método para eliminar un usuario por ID (HTTP DELETE)
    @DeleteMapping("/usuarios/{id}")
    @ResponseBody
    public ResponseEntity<String> eliminarUsuario(@PathVariable Integer id) {
        if (usuariosRepository.existsById(id)) {
            usuariosRepository.deleteById(id);
            return new ResponseEntity<>("Usuario eliminado exitosamente", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Usuario no encontrado", HttpStatus.NOT_FOUND);
        }
    }

    // Método para actualizar un usuario (HTTP PUT)
    @PutMapping("/usuarios/{id}")
    @ResponseBody
    public ResponseEntity<String> editarUsuario(@PathVariable Integer id, @RequestBody Usuarios usuarioActualizado) {
        Optional<Usuarios> usuarioExistenteOpt = usuariosRepository.findById(id);
        if (usuarioExistenteOpt.isPresent()) {
            Usuarios usuarioExistente = usuarioExistenteOpt.get();
            // Actualizar solo si los valores son proporcionados
            if (usuarioActualizado.getNombre() != null && !usuarioActualizado.getNombre().isEmpty()) {
                usuarioExistente.setNombre(usuarioActualizado.getNombre());
            }
            if (usuarioActualizado.getApellido() != null && !usuarioActualizado.getApellido().isEmpty()) {
                usuarioExistente.setApellido(usuarioActualizado.getApellido());
            }
            // No es recomendable cambiar el ID ni la contraseña aquí
            usuariosRepository.save(usuarioExistente);
            return new ResponseEntity<>("Usuario actualizado exitosamente", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Usuario no encontrado", HttpStatus.NOT_FOUND);
        }
    }

    // Nuevo método para la autenticación
    @PostMapping("/auth")
    @ResponseBody
    public ResponseEntity<?> autenticarUsuario(@RequestBody AuthRequest authRequest) {
        String username = authRequest.getUsername();
        String password = authRequest.getPassword();

        // Validar si el usuario y la contraseña son vacíos
        if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El usuario y la contraseña son requeridos");
        }

        // Buscar el usuario en la base de datos
        Optional<Usuarios> usuarioOpt = usuariosRepository.findByUsername(username);
        if (usuarioOpt.isPresent()) {
            Usuarios usuario = usuarioOpt.get();
            int idUsuario = usuario.getId();
            String nombreUsuario = usuario.getNombre();
            String apellidoUsuario = usuario.getApellido();
            String correoUsuario = usuario.getCorreoelectronico();
            String rolUsuario = usuario.getRol().getTipodeRol();

            // Verificar la contraseña usando BCrypt
            if (passwordEncoder.matches(password, usuario.getPassword())) {

                Map<String, Object> claims = new HashMap<>();
                claims.put("rol", rolUsuario); // Añade roles o cualquier otra información
                claims.put("id", idUsuario);
                claims.put("nombre", nombreUsuario);
                claims.put("apellido", apellidoUsuario);
                claims.put("correo", correoUsuario);

                // Fecha de expiración del token (ej. 10 minutos)
                long expirationTime = 1000 * 60 * 120;
                // Generar el token JWT
                String token = Jwts.builder()
                        .setClaims(claims)
                        .setSubject(username)
                        .setIssuedAt(new Date())
                        .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                        .signWith(SECRET_KEY)
                        .compact();

                // Devolver el token en el cuerpo de la respuesta
                return ResponseEntity.ok(token);

            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Contraseña incorrecta");
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no registrado");
        }
    }

    @GetMapping("/verify-token")
    public ResponseEntity<?> verifyToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token no proporcionado");
        }

        String token = authHeader.substring(7); // Extrae el token del header

        try {
            // Verifica y extrae los claims del token
            Claims claims = Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token)
                .getBody();

            // Si el token es válido, puedes retornar los claims o cualquier información que necesites
            return ResponseEntity.ok(claims);
        } catch (SignatureException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token inválido");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al verificar el token");
        }
    }


    // Método para registrar un nuevo usuario (HTTP POST)
    @PostMapping("/registrar")
    public ResponseEntity<String> registerUser(@RequestBody UserRegistrationDetails registrationDetails) {
        try {
            // Crear un nuevo objeto de usuario
            Usuarios nuevoUsuario = new Usuarios();
            nuevoUsuario.setNombre(registrationDetails.getNombre());
            nuevoUsuario.setApellido(registrationDetails.getApellido());
            nuevoUsuario.setCorreoelectronico(registrationDetails.getcorreoelectronico());
            nuevoUsuario.setUsername(registrationDetails.getUsername());
            nuevoUsuario.setPassword(passwordEncoder.encode(registrationDetails.getPassword()));

            // Obtener el idRol del objeto rol
            int idRol = registrationDetails.getRol().getIdRol();
            // Buscar el rol por id
            Roles rol = rolRepository.findById(idRol)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));
            System.out.println("Rol encontrado: " + rol.getTipodeRol());
            // Asignar el rol encontrado al usuario
            nuevoUsuario.setRol(rol);

            // Guardar el usuario en la base de datos
            Usuarios usuarioGuardado = usuariosRepository.save(nuevoUsuario);

            // Si el usuario se ha guardado exitosamente, enviamos el correo en un nuevo hilo
            if (usuarioGuardado != null) {
                new Thread(() -> {
                    try {
                        EmailDetails emailDetails = new EmailDetails();
                        emailDetails.setRecipient(nuevoUsuario.getCorreoelectronico());
                        emailDetails.setSubject("Bienvenido a nuestro servicio");

                        // Cuerpo del correo HTML
                        String htmlBody = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <style>
                                        body {
                                            font-family: Arial, sans-serif;
                                            background-color: #f4f4f4;
                                            padding: 20px;
                                        }
                                        .email-container {
                                            background-color: white;
                                            padding: 20px;
                                            border-radius: 10px;
                                            max-width: 600px;
                                            margin: auto;
                                            box-shadow: 0 0 10px rgba(0,0,0,0.1);
                                        }
                                        h1 {
                                            color: #333;
                                        }
                                        p {
                                            color: #666;
                                        }
                                        .footer {
                                            margin-top: 20px;
                                            text-align: center;
                                            font-size: 12px;
                                            color: #999;
                                        }
                                    </style>
                                </head>
                                <body>
                                    <div class="email-container">
                                        <h1>¡Bienvenido a nuestra plataforma, %s!</h1>
                                        <p>Gracias por registrarte, %s %s.</p>
                                        <p>Estamos emocionados de tenerte con nosotros.</p>
                                        <p>Si tienes alguna duda, no dudes en contactarnos.</p>
                                        <div class="footer">
                                            <p>© 2024 Nuestra Empresa. Todos los derechos reservados.</p>
                                        </div>
                                    </div>
                                </body>
                                </html>
                                """.formatted(nuevoUsuario.getNombre(), nuevoUsuario.getNombre(), nuevoUsuario.getApellido());

                        emailDetails.setMsgBody(htmlBody);
                        emailService.sendHtmlMail(emailDetails);
                    } catch (Exception e) {
                        // Manejo de errores al enviar el correo
                        System.err.println("Error al enviar el correo: " + e.getMessage());
                    }
                }).start();

                return new ResponseEntity<>("Usuario registrado y bienvenido", HttpStatus.CREATED);
            } else {
                return new ResponseEntity<>("Error al registrar el usuario", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            return new ResponseEntity<>("Ocurrió un error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    // Método para editar un usuario existente (HTTP PUT)
    @PutMapping("/editar/{id}")
    public ResponseEntity<String> editarUsuario(@PathVariable int id,
            @RequestBody UserRegistrationDetails registrationDetails) {
        Optional<Usuarios> usuarioExistente = usuariosRepository.findById(id);

        if (usuarioExistente.isPresent()) {
            Usuarios usuario = usuarioExistente.get();
            usuario.setCorreoelectronico(registrationDetails.getcorreoelectronico());

            // Actualizar la contraseña si es necesario
            if (registrationDetails.getPassword() != null && !registrationDetails.getPassword().isEmpty()) {
                usuario.setPassword(passwordEncoder.encode(registrationDetails.getPassword()));
            }

            usuariosRepository.save(usuario);
            return new ResponseEntity<>("Usuario actualizado exitosamente.", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Usuario no encontrado.", HttpStatus.NOT_FOUND);
        }
    }

    // Método para eliminar un usuario existente (HTTP DELETE)
    @DeleteMapping("/eliminar/{id}")
    public ResponseEntity<String> eliminarUsuario(@PathVariable int id) {
        Optional<Usuarios> usuarioExistente = usuariosRepository.findById(id);

        if (usuarioExistente.isPresent()) {
            usuariosRepository.deleteById(id);
            return new ResponseEntity<>("Usuario eliminado exitosamente.", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Usuario no encontrado.", HttpStatus.NOT_FOUND);
        }
    }

    //Insertar automaticamente los roles en la base de datos
    @Configuration
    public class LoadDatabase {

        @Bean
        CommandLineRunner initRolesDatabase(RolRepository rolRepository) {
            return args -> {
                if (rolRepository.findByTipodeRol("Administrador") == null) {
                    rolRepository.save(new Roles(1, "Administrador"));
                }
                if (rolRepository.findByTipodeRol("Usuario") == null) {
                    rolRepository.save(new Roles(2, "Usuario"));
                }
            };
        }

        @Bean
        CommandLineRunner initUsersDatabase(UsuariosRepository usuariosRepository) {
                return args -> {
                    // Esperar unos segundos antes de insertar el usuario
                try {
                    Thread.sleep(5000);  // 5 segundos de espera
                    Roles rol = new Roles(1, "Administrador"); 
                    usuariosRepository.save(new Usuarios(1, "Home", "Book", "$2a$10$IfxIObfqEM76kuQhb/12/uT5O8dEjz6HEoNMjm2gxl3Ej4/lV6PGa", "BookHaven", "BookHaven.com", rol));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                
            };
        }
    }

}