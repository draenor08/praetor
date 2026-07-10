import { Component, inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { CommonModule } from '@angular/common';

function passwordMatchValidator(control: AbstractControl): ValidationErrors | null {
  const password = control.get('password');
  const confirmPassword = control.get('confirmPassword');

  if (password && confirmPassword && password.value !== confirmPassword.value) {
    confirmPassword.setErrors({ ...confirmPassword.errors, passwordMismatch: true });
    return { passwordMismatch: true };
  } else {
    if (confirmPassword && confirmPassword.errors) {
       const errors = { ...confirmPassword.errors };
       delete errors['passwordMismatch'];
       if (Object.keys(errors).length === 0) {
         confirmPassword.setErrors(null);
       } else {
         confirmPassword.setErrors(errors);
       }
    }
  }
  return null;
}

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss']
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);

  registerForm: FormGroup = this.fb.group({
    fullName: ['', Validators.required],
    username: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required]
  }, { validators: passwordMatchValidator });

  errorMessage: string = '';

  onSubmit() {
    if (this.registerForm.valid) {
      this.authService.register(this.registerForm.value).subscribe({
        next: () => {
          this.router.navigate(['/']); // redirect to home
        },
        error: (err) => {
          if (err.error && typeof err.error === 'object') {
             // Handle backend validation errors or custom message
             if (err.error.message) {
                 this.errorMessage = err.error.message;
             } else {
                 this.errorMessage = Object.values(err.error).join(', ');
             }
          } else {
             this.errorMessage = 'Registration failed. Please try again.';
          }
        }
      });
    }
  }
}
