CREATE TABLE learning_plans (
 id uuid PRIMARY KEY,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,
 student_id uuid NOT NULL REFERENCES accounts(id),subject varchar(32) NOT NULL,
 title varchar(120) NOT NULL,long_term_goal varchar(2000) NOT NULL,
 status varchar(16) NOT NULL CHECK(status IN('ACTIVE','ARCHIVED')),
 revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0)
);
CREATE INDEX ix_plan_student ON learning_plans(student_id,created_at DESC);
CREATE TABLE plan_milestones (
 id uuid PRIMARY KEY,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,
 plan_id uuid NOT NULL REFERENCES learning_plans(id),title varchar(120) NOT NULL,
 goal varchar(2000) NOT NULL,target_date date,completed boolean NOT NULL DEFAULT false
);
CREATE INDEX ix_milestone_plan ON plan_milestones(plan_id);
CREATE TABLE plan_task_links (
 id uuid PRIMARY KEY,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,
 plan_id uuid NOT NULL REFERENCES learning_plans(id),task_id uuid NOT NULL REFERENCES learning_tasks(id),
 CONSTRAINT ux_plan_task UNIQUE(plan_id,task_id)
);
CREATE TABLE plan_changes (
 id uuid PRIMARY KEY,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,
 plan_id uuid NOT NULL REFERENCES learning_plans(id),actor_id uuid NOT NULL REFERENCES accounts(id),
 revision bigint NOT NULL,action varchar(32) NOT NULL,details text NOT NULL,
 CONSTRAINT ux_plan_change_revision UNIQUE(plan_id,revision)
);
CREATE INDEX ix_plan_change ON plan_changes(plan_id,revision DESC);
