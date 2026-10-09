-- Ported from feat/payroll (af2926c): db/changelog/payroll/1.0.0.4-employee-profile-and-contract-fields.xml
-- Enum types for the employee sheet fields plus the bank and province reference tables.

CREATE TYPE payroll.gender             AS ENUM ('MALE','FEMALE','OTHER');
CREATE TYPE payroll.marital_status     AS ENUM ('SINGLE','MARRIED','DIVORCED');
CREATE TYPE payroll.education_level    AS ENUM ('GENERAL_LABOR','VOCATIONAL','COLLEGE','UNIVERSITY','DOCTORATE');
CREATE TYPE payroll.education_grade    AS ENUM ('EXCELLENT','GOOD','FAIR','AVERAGE_FAIR','AVERAGE');
CREATE TYPE payroll.recruitment_source AS ENUM ('GOOGLE_FORM','EMAIL','IN_HOUSE','REFERENCE','SOCIAL','CORE_TEAM');
CREATE TYPE payroll.termination_reason AS ENUM (
  'UNSUITABLE_ENVIRONMENT','SKILL_MISMATCH','NEW_JOB','PERSONAL_PLAN','SCHEDULE_CONFLICT','SALARY_DISAGREEMENT');
CREATE TYPE payroll.job_level          AS ENUM (
  'ENTRY_LEVEL','JUNIOR','SENIOR','SPECIALIST','TEAM_LEADER','SUPERVISOR',
  'JUNIOR_MANAGER','SENIOR_MANAGER','HEAD_DIRECTOR','MANAGER');
CREATE TYPE payroll.contract_kind      AS ENUM (
  'PROBATION','FIXED_TERM_FT','INDEFINITE_FT','PART_TIME_AGREEMENT','SERVICE');
CREATE TYPE payroll.probation_result   AS ENUM ('PASSED','FAILED');

CREATE TABLE payroll.bank (
  bank_code  TEXT PRIMARY KEY,
  bank_name  TEXT NOT NULL,
  short_name TEXT NOT NULL,
  bin        TEXT,
  is_active  BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE payroll.province (
  province_code TEXT PRIMARY KEY,
  province_name TEXT NOT NULL
);

INSERT INTO payroll.bank (bank_code, bank_name, short_name, bin) VALUES
  ('VCB',  'Ngan hang TMCP Ngoai thuong Viet Nam',          'Vietcombank', '970436'),
  ('TCB',  'Ngan hang TMCP Ky thuong Viet Nam',             'Techcombank', '970407'),
  ('MB',   'Ngan hang TMCP Quan doi',                       'MB Bank',     '970422'),
  ('ACB',  'Ngan hang TMCP A Chau',                         'ACB',         '970416'),
  ('VPB',  'Ngan hang TMCP Viet Nam Thinh Vuong',           'VPBank',      '970432'),
  ('TPB',  'Ngan hang TMCP Tien Phong',                     'TPBank',      '970423'),
  ('BIDV', 'Ngan hang TMCP Dau tu va Phat trien Viet Nam',  'BIDV',        '970418'),
  ('TIMO', 'Timo Digital Bank',                             'Timo',        NULL);

INSERT INTO payroll.province (province_code, province_name) VALUES
  ('AN_GIANG', 'An Giang'),
  ('BA_RIA_VUNG_TAU', 'Bà Rịa - Vũng Tàu'),
  ('BAC_GIANG', 'Bắc Giang'),
  ('BAC_KAN', 'Bắc Kạn'),
  ('BAC_NINH', 'Bắc Ninh'),
  ('BAC_LIEU', 'Bạc Liêu'),
  ('BEN_TRE', 'Bến Tre'),
  ('BINH_DUONG', 'Bình Dương'),
  ('BINH_DINH', 'Bình Định'),
  ('BINH_PHUOC', 'Bình Phước'),
  ('BINH_THUAN', 'Bình Thuận'),
  ('CA_MAU', 'Cà Mau'),
  ('CAO_BANG', 'Cao Bằng'),
  ('CAN_THO', 'Cần Thơ'),
  ('DA_NANG', 'Đà Nẵng'),
  ('DAK_LAK', 'Đắk Lắk'),
  ('DAK_NONG', 'Đắk Nông'),
  ('DIEN_BIEN', 'Điện Biên'),
  ('DONG_NAI', 'Đồng Nai'),
  ('DONG_THAP', 'Đồng Tháp'),
  ('GIA_LAI', 'Gia Lai'),
  ('HA_GIANG', 'Hà Giang'),
  ('HA_NAM', 'Hà Nam'),
  ('HA_NOI', 'Hà Nội'),
  ('HA_TINH', 'Hà Tĩnh'),
  ('HAI_DUONG', 'Hải Dương'),
  ('HAI_PHONG', 'Hải Phòng'),
  ('HAU_GIANG', 'Hậu Giang'),
  ('HOA_BINH', 'Hòa Bình'),
  ('TP_HO_CHI_MINH', 'TP. Hồ Chí Minh'),
  ('HUNG_YEN', 'Hưng Yên'),
  ('KHANH_HOA', 'Khánh Hòa'),
  ('KIEN_GIANG', 'Kiên Giang'),
  ('KON_TUM', 'Kon Tum'),
  ('LAI_CHAU', 'Lai Châu'),
  ('LAM_DONG', 'Lâm Đồng'),
  ('LANG_SON', 'Lạng Sơn'),
  ('LAO_CAI', 'Lào Cai'),
  ('LONG_AN', 'Long An'),
  ('NAM_DINH', 'Nam Định'),
  ('NGHE_AN', 'Nghệ An'),
  ('NINH_BINH', 'Ninh Bình'),
  ('NINH_THUAN', 'Ninh Thuận'),
  ('PHU_THO', 'Phú Thọ'),
  ('PHU_YEN', 'Phú Yên'),
  ('QUANG_BINH', 'Quảng Bình'),
  ('QUANG_NAM', 'Quảng Nam'),
  ('QUANG_NGAI', 'Quảng Ngãi'),
  ('QUANG_NINH', 'Quảng Ninh'),
  ('QUANG_TRI', 'Quảng Trị'),
  ('SOC_TRANG', 'Sóc Trăng'),
  ('SON_LA', 'Sơn La'),
  ('TAY_NINH', 'Tây Ninh'),
  ('THAI_BINH', 'Thái Bình'),
  ('THAI_NGUYEN', 'Thái Nguyên'),
  ('THANH_HOA', 'Thanh Hóa'),
  ('THUA_THIEN_HUE', 'Thừa Thiên Huế'),
  ('TIEN_GIANG', 'Tiền Giang'),
  ('TRA_VINH', 'Trà Vinh'),
  ('TUYEN_QUANG', 'Tuyên Quang'),
  ('VINH_LONG', 'Vĩnh Long'),
  ('VINH_PHUC', 'Vĩnh Phúc'),
  ('YEN_BAI', 'Yên Bái');
