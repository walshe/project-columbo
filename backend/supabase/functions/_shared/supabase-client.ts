// Supabase client factory for Edge Functions.
// Uses the service_role key for full read/write access (bypasses RLS).

import { createClient, SupabaseClient } from "npm:@supabase/supabase-js@2";

let _client: SupabaseClient | null = null;

/**
 * Returns a singleton Supabase client using service_role credentials.
 * Environment variables SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY must be set.
 */
export function getSupabaseClient(): SupabaseClient {
  if (_client) return _client;

  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

  if (!url || !key) {
    throw new Error(
      "Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY environment variables"
    );
  }

  _client = createClient(url, key, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  return _client;
}
