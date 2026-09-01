import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { api, ApiError, getToken, setToken } from "../lib/api";
import type { AuthResponse, RegisterRequest, RoleName, UserResponse } from "../types";

interface LoginRequest {
  email: string;
  password: string;
}

interface AuthContextValue {
  user: UserResponse | null;
  loading: boolean;
  login: (body: LoginRequest) => Promise<void>;
  register: (body: RegisterRequest) => Promise<void>;
  logout: () => void;
  hasRole: (...roles: RoleName[]) => boolean;
  isManager: boolean;
  isTenant: boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function bootstrap() {
      if (!getToken()) {
        setLoading(false);
        return;
      }
      try {
        const me = await api.get<UserResponse>("/api/auth/me");
        if (!cancelled) setUser(me);
      } catch (err) {
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
          setToken(null);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    bootstrap();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (body: LoginRequest) => {
    const res = await api.post<AuthResponse>("/api/auth/login", body);
    setToken(res.token);
    setUser(res.user);
  }, []);

  const register = useCallback(async (body: RegisterRequest) => {
    const res = await api.post<AuthResponse>("/api/auth/register", body);
    setToken(res.token);
    setUser(res.user);
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
  }, []);

  const hasRole = useCallback(
    (...roles: RoleName[]) => !!user && roles.some((r) => user.roles.includes(r)),
    [user]
  );

  const value = useMemo(
    () => ({
      user,
      loading,
      login,
      register,
      logout,
      hasRole,
      isManager: hasRole("ADMIN", "PROPERTY_MANAGER"),
      isTenant: hasRole("TENANT") && !hasRole("ADMIN", "PROPERTY_MANAGER")
    }),
    [user, loading, login, register, logout, hasRole]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}
