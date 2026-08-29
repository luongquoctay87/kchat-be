CREATE TABLE IF NOT EXISTS user_contacts (
  owner_id         UUID NOT NULL,
  contact_user_id  UUID NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT user_contacts_pkey PRIMARY KEY (owner_id, contact_user_id),
  CONSTRAINT user_contacts_owner_id_fkey
    FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT user_contacts_contact_user_id_fkey
    FOREIGN KEY (contact_user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT user_contacts_not_self_chk CHECK (owner_id <> contact_user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_contacts_contact_user_id
  ON user_contacts (contact_user_id);
