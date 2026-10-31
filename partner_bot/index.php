<?php
session_start();
require_once __DIR__ . '/../db.php';

// Handle Logout
if (isset($_GET['logout'])) {
    session_destroy();
    header('Location: /partner_bot/');
    exit;
}

// Handle Login
if (!isset($_SESSION['user_id'])) {
    $login_error = '';
    if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['username'])) {
        $username = trim($_POST['username'] ?? '');
        $password = trim($_POST['password'] ?? '');
        $stmt = $pdo->prepare("SELECT * FROM system_users WHERE username = ?");
        $stmt->execute([$username]);
        $user = $stmt->fetch(PDO::FETCH_ASSOC);
        if ($user && password_verify($password, $user['password'])) {
            $_SESSION['user_id'] = $user['id'];
            $_SESSION['role'] = $user['role'];
            $_SESSION['username'] = $user['username'];
            header('Location: /partner_bot/');
            exit;
        } else {
            $login_error = 'Invalid username or password';
        }
    }
    ?>
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
        <title>TMA Login</title>
        <script src="https://telegram.org/js/telegram-web-app.js"></script>
        <script src="https://unpkg.com/lucide@latest"></script>
        <style>
            :root {
                --bg: #0a0a0a;
                --surface: #121212;
                --card: #1a1a1a;
                --border: #262626;
                --text: #e8e8e8;
                --text-2: #999;
                --text-3: #555;
                --accent: #66FCF1;
                --accent-lt: #9DFDF6;
                --accent-dim: #1F4A47;
                --red: #ef4444;
            }
            body { 
                background: var(--bg); 
                color: var(--text); 
                display:flex; 
                flex-direction: column;
                align-items:center; 
                justify-content:center; 
                min-height:100vh; 
                padding:20px; 
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; 
                margin:0; 
                box-sizing: border-box;
            }
            .login-card { 
                width: 100%; 
                max-width: 340px; 
                text-align: center;
                background: var(--card);
                padding: 40px 24px;
                border-radius: 24px;
                border: 1px solid var(--border);
                box-shadow: 0 12px 40px rgba(0,0,0,0.6);
            }
            .logo-icon {
                width: 72px;
                height: 72px;
                background: var(--accent-dim);
                margin: 0 auto 28px auto;
                border-radius: 20px;
                display: flex;
                align-items: center;
                justify-content: center;
                color: var(--accent-lt);
                box-shadow: 0 4px 12px rgba(102,252,241,0.2);
            }
            h2 { margin: 0 0 8px 0; font-size: 26px; font-weight: 800; color: #fff; letter-spacing: -0.5px; }
            .subtitle { color: var(--text-2); font-size: 15px; margin-bottom: 36px; font-weight: 500; }
            
            .input-group { margin-bottom: 20px; text-align: left; }
            .input-group label { display: block; margin-bottom: 8px; font-size: 12px; font-weight: 700; color: var(--text-2); padding-left: 4px; text-transform: uppercase; letter-spacing: 0.5px; }
            .input-group input { 
                width: 100%; 
                padding: 16px; 
                border-radius: 14px; 
                border: 1px solid var(--border); 
                background: var(--surface); 
                color: var(--text); 
                box-sizing: border-box; 
                font-size: 16px;
                transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
            }
            .input-group input:focus {
                border-color: var(--accent);
                outline: none;
                box-shadow: 0 0 0 4px rgba(102,252,241,0.1);
            }
            .btn { 
                width: 100%; 
                padding: 18px; 
                border: none; 
                border-radius: 14px; 
                background: var(--accent); 
                color: #000; 
                font-weight: 800; 
                font-size: 17px; 
                cursor: pointer; 
                margin-top: 10px;
                transition: all 0.2s; 
                box-shadow: 0 4px 12px rgba(102,252,241,0.2);
            }
            .btn:active { transform: scale(0.97); opacity: 0.9; }
            .error { color: #ff4444; font-size: 14px; text-align: center; margin-bottom: 20px; background: rgba(255,68,68,0.1); padding: 12px; border-radius: 12px; font-weight: 600; border: 1px solid rgba(255,68,68,0.2); }
        </style>
    </head>
    <body>
        <div class="login-card">
            <div class="logo-icon"><i data-lucide="shield-check" style="width:36px; height:36px;"></i></div>
            <h2>Partner Access</h2>
            <div class="subtitle">Equinox Management System</div>
            
            <?php if ($login_error): ?><div class="error"><?= htmlspecialchars($login_error) ?></div><?php endif; ?>
            
            <form method="POST" action="">
                <div class="input-group">
                    <label>Username</label>
                    <input type="text" name="username" required autocomplete="off" placeholder="Enter your login">
                </div>
                <div class="input-group">
                    <label>Password</label>
                    <input type="password" name="password" required placeholder="••••••••">
                </div>
                <button type="submit" class="btn">Sign In</button>
            </form>
        </div>
        <script>
            lucide.createIcons();
            if (window.Telegram && window.Telegram.WebApp) {
                window.Telegram.WebApp.expand();
                window.Telegram.WebApp.headerColor = '#121212';
            }
        </script>
    </body>
    </html>
    <?php
    exit;
}

$role = $_SESSION['role'];
$user_id = $_SESSION['user_id'];
$sys_username = $_SESSION['username'];

// Handle Actions
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['action'])) {
    if ($_POST['action'] === 'add_app' && $role === 'admin') {
        $name = trim($_POST['name'] ?? '');
        if ($name) {
            $stmt = $pdo->prepare("INSERT INTO apps (name, description, features) VALUES (?, '', '')");
            $stmt->execute([$name]);
        }
    } elseif ($_POST['action'] === 'add_build' && $role === 'admin') {
        $app_id = intval($_POST['app_id'] ?? 0);
        $version = trim($_POST['version'] ?? '');
        $owner_id = intval($_POST['owner_id'] ?? 0);
        if ($app_id && $version && $owner_id) {
            $stmt = $pdo->prepare("INSERT INTO builds (owner_id, app_id, app_name, version, created_at) VALUES (?, ?, (SELECT name FROM apps WHERE id=?), ?, datetime('now'))");
            $stmt->execute([$owner_id, $app_id, $app_id, $version]);
        }
    }
    header('Location: /partner_bot/');
    exit;
}

// Fetch Data logic
$partnerBuildIn = '(0)';
$app_filter_logs = "1=1";
$app_filter_cli = "1=1";

if ($role !== 'admin') {
    $paStmt = $pdo->prepare("SELECT id, app_id FROM builds WHERE owner_id = ?");
    $paStmt->execute([$user_id]);
    $buildsRes = $paStmt->fetchAll(PDO::FETCH_ASSOC);
    if (!empty($buildsRes)) {
        $partnerBuildIds = array_column($buildsRes, 'id');
        $partnerBuildIn = '(' . implode(',', array_map('intval', $partnerBuildIds)) . ')';
        
        $partnerAppIds = array_unique(array_column($buildsRes, 'app_id'));
        if(!empty($partnerAppIds)) {
            $app_filter_logs = "app_id IN (" . implode(',', array_map('intval', $partnerAppIds)) . ")";
        } else {
            $app_filter_logs = "0=1";
        }
    } else {
        $app_filter_logs = "0=1";
    }
    $app_filter_cli = "build_id IN $partnerBuildIn";
}

// Apps & Builds
if ($role === 'admin') {
    $apps = $pdo->query("SELECT * FROM apps ORDER BY name ASC")->fetchAll(PDO::FETCH_ASSOC);
    $builds = $pdo->query("
        SELECT b.*, a.name as original_app_name, a.icon as app_icon, su.username as owner_username 
        FROM builds b 
        LEFT JOIN apps a ON b.app_id = a.id 
        LEFT JOIN system_users su ON b.owner_id = su.id 
        ORDER BY b.created_at DESC LIMIT 50
    ")->fetchAll(PDO::FETCH_ASSOC);
} else {
    $apps = $pdo->query("SELECT DISTINCT a.* FROM apps a JOIN builds b ON a.id = b.app_id WHERE b.owner_id = $user_id ORDER BY a.name ASC")->fetchAll(PDO::FETCH_ASSOC);
    $builds = $pdo->query("
        SELECT b.*, a.name as original_app_name, a.icon as app_icon, su.username as owner_username 
        FROM builds b 
        LEFT JOIN apps a ON b.app_id = a.id 
        LEFT JOIN system_users su ON b.owner_id = su.id 
        WHERE b.owner_id = $user_id
        ORDER BY b.created_at DESC LIMIT 50
    ")->fetchAll(PDO::FETCH_ASSOC);
}

// Date filtering logic
$period = $_GET['period'] ?? 'today';
$active_tab = $_GET['tab'] ?? 'main';

function getDc($p, $c) {
    if ($p=='today') return "date($c) = date('now')";
    if ($p=='yesterday') return "date($c) = date('now', '-1 day')";
    if ($p=='this_week') return "date($c) >= date('now', 'weekday 1', '-7 days')";
    if ($p=='this_month') return "date($c) >= date('now', 'start of month')";
    if ($p=='last_week') return "date($c) >= date('now', 'weekday 1', '-14 days') AND date($c) < date('now', 'weekday 1', '-7 days')";
    if ($p=='last_month') return "date($c) >= date('now', 'start of month', '-1 month') AND date($c) < date('now', 'start of month')";
    if ($p=='week') return "date($c) >= date('now', '-7 days')";
    if ($p=='month') return "date($c) >= date('now', '-30 days')";
    return "1=1";
}

$dc_log_today = "date(date) = date('now')";
$dc_cli_today = "date(created_at) = date('now')";

// Main Tab Stats (Daily Overview - forced to today)
$stats_main = [];
$stats_main['Active Users'] = ['val' => $pdo->query("SELECT COUNT(DISTINCT device_id) FROM logs WHERE $dc_log_today AND $app_filter_logs")->fetchColumn(), 'icon' => 'user-check'];
$stats_main['New Users'] = ['val' => $pdo->query("SELECT COUNT(*) FROM clients WHERE $dc_cli_today AND $app_filter_cli")->fetchColumn(), 'icon' => 'user-plus'];
$stats_main['Users Uploaded'] = ['val' => $pdo->query("SELECT COUNT(DISTINCT device_id) FROM logs WHERE log_type='files' AND $dc_log_today AND $app_filter_logs")->fetchColumn(), 'icon' => 'upload-cloud'];
$stats_main['Locations'] = ['val' => $pdo->query("SELECT COUNT(DISTINCT device_id) FROM logs WHERE log_type='location' AND $dc_log_today AND $app_filter_logs")->fetchColumn(), 'icon' => 'map-pin'];

// Recent Actions (Main Tab)
$recent_actions = $pdo->query("
    SELECT log_type, log_msg, date, app_id 
    FROM logs 
    WHERE log_type IN ('application', 'session', 'files', 'location', 'authentication') 
      AND $app_filter_logs
    ORDER BY id DESC 
    LIMIT 20
")->fetchAll(PDO::FETCH_ASSOC);

function mapActionName($t) {
    if ($t == 'application' || $t == 'session') return 'Open App';
    if ($t == 'files') return 'Files Uploading';
    if ($t == 'location') return 'Received Location';
    if ($t == 'authentication') return 'Authentication';
    return ucfirst($t);
}

// Stats Tab Stats (With period selection)
$dc_log_period = getDc($period, 'date');
$dc_cli_period = getDc($period, 'created_at');

$stats_period = [];
$stats_period['Active Users'] = ['val' => $pdo->query("SELECT COUNT(DISTINCT device_id) FROM logs WHERE $dc_log_period AND $app_filter_logs")->fetchColumn(), 'icon' => 'user-check'];
$stats_period['New Users'] = ['val' => $pdo->query("SELECT COUNT(*) FROM clients WHERE $dc_cli_period AND $app_filter_cli")->fetchColumn(), 'icon' => 'user-plus'];
$stats_period['Users Uploaded'] = ['val' => $pdo->query("SELECT COUNT(DISTINCT device_id) FROM logs WHERE log_type='files' AND $dc_log_period AND $app_filter_logs")->fetchColumn(), 'icon' => 'upload-cloud'];
$stats_period['Locations'] = ['val' => $pdo->query("SELECT COUNT(DISTINCT device_id) FROM logs WHERE log_type='location' AND $dc_log_period AND $app_filter_logs")->fetchColumn(), 'icon' => 'map-pin'];

$team_stats = [];
$system_users = [];
if ($role === 'admin') {
    $system_users = $pdo->query("SELECT id, username FROM system_users WHERE role = 'partner' ORDER BY username ASC")->fetchAll(PDO::FETCH_ASSOC);
    
    foreach ($system_users as $t) {
        $apps_res = $pdo->prepare("SELECT DISTINCT a.id, a.name FROM apps a JOIN builds b ON a.id=b.app_id WHERE b.owner_id=?");
        $apps_res->execute([$t['id']]);
        $apps_arr = $apps_res->fetchAll(PDO::FETCH_ASSOC);
        foreach ($apps_arr as &$a) {
            $app_id = $a['id'];
            $a['opens'] = $pdo->query("SELECT COUNT(*) FROM logs WHERE app_id=$app_id AND log_type IN ('application', 'session') AND $dc_log_period")->fetchColumn();
            $a['files'] = $pdo->query("SELECT COUNT(*) FROM logs WHERE app_id=$app_id AND log_type='files' AND $dc_log_period")->fetchColumn();
            $a['locations'] = $pdo->query("SELECT COUNT(*) FROM logs WHERE app_id=$app_id AND log_type='location' AND $dc_log_period")->fetchColumn();
            $a['auths'] = $pdo->query("SELECT COUNT(*) FROM logs WHERE app_id=$app_id AND log_type='authentication' AND $dc_log_period")->fetchColumn();
        }
        $t['apps'] = $apps_arr;
        $team_stats[] = $t;
    }
}

function hsc($v) { return htmlspecialchars($v ?? '', ENT_QUOTES); }
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
    <title>TMA Admin</title>
    <script src="https://telegram.org/js/telegram-web-app.js"></script>
    <script src="https://unpkg.com/lucide@latest"></script>
    <style>
        :root {
            --bg: #0a0a0a;
            --surface: #121212;
            --card: #1a1a1a;
            --border: #262626;
            --border-lt: #333;
            --text: #e8e8e8;
            --text-2: #999;
            --text-3: #555;
            --accent: #66FCF1;
            --accent-lt: #9DFDF6;
            --accent-dim: #1F4A47;
            --header-h: 68px;
            --nav-h: 76px;
            --red: #ef4444;
        }
        * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
        body { 
            background: var(--bg); 
            color: var(--text); 
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; 
            margin:0; 
            padding: calc(var(--header-h) + 16px) 16px calc(var(--nav-h) + 24px) 16px;
            overflow-x: hidden;
        }

        /* Fixed Header */
        header {
            position: fixed; top: 0; left: 0; right: 0; height: var(--header-h);
            background: rgba(18, 18, 18, 0.9); z-index: 50; display: flex; align-items: center; justify-content: space-between;
            padding: 0 16px; border-bottom: 1px solid var(--border); backdrop-filter: blur(12px);
        }
        header h1 { font-size: 20px; margin: 0; font-weight: 800; letter-spacing: -0.6px; }
        .user-tag { font-size: 10px; color: var(--accent-lt); background: var(--accent-dim); padding: 4px 8px; border-radius: 6px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.5px; }
        
        /* Stats Grid */
        .stats-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 28px; }
        .stat-card { background: var(--card); padding: 20px 16px; border-radius: 20px; border: 1px solid var(--border); display: flex; flex-direction: column; align-items: flex-start; position: relative; overflow: hidden; }
        .stat-card .val { font-size: 28px; font-weight: 900; margin-bottom: 4px; color: var(--accent-lt); letter-spacing: -1px; position: relative; z-index: 2; }
        .stat-card .lbl { font-size: 10px; font-weight: 800; color: var(--text-3); text-transform: uppercase; letter-spacing: 1px; position: relative; z-index: 2; }
        .stat-card i { position: absolute; right: -10px; bottom: -10px; width: 64px; height: 64px; color: rgba(102,252,241,0.04); transform: rotate(-15deg); z-index: 1; }

        /* Sections */
        .section-title { font-size: 18px; font-weight: 800; margin: 0 0 14px 4px; display: flex; justify-content: space-between; align-items: center; color: #fff; letter-spacing: -0.3px; }
        .add-btn { background: var(--accent); color: #000; border: none; padding: 8px 16px; border-radius: 12px; font-size: 13px; font-weight: 800; cursor: pointer; display: flex; align-items: center; gap: 6px; }

        /* Lists */
        .list-card { background: var(--card); border-radius: 20px; overflow: hidden; margin-bottom: 24px; border: 1px solid var(--border); }
        .item { display: flex; align-items: center; padding: 16px; border-bottom: 1px solid var(--border); position: relative; }
        .item:last-child { border-bottom: none; }
        .item:active { background: rgba(255,255,255,0.03); }
        .item-icon { width: 48px; height: 48px; border-radius: 14px; background: var(--surface); margin-right: 14px; display: flex; align-items: center; justify-content: center; overflow: hidden; border: 1px solid var(--border); flex-shrink: 0; }
        .item-icon img { width: 100%; height: 100%; object-fit: contain; }
        .item-icon.placeholder { font-weight: 800; color: var(--accent-dim); font-size: 24px; background: var(--accent-lt); }
        .item-content { flex: 1; min-width: 0; }
        .item-title { font-size: 16px; font-weight: 700; margin-bottom: 3px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #fff; }
        .item-sub { font-size: 12px; color: var(--text-2); font-weight: 500; }
        .item-badge { padding: 4px 10px; border-radius: 8px; font-size: 11px; font-weight: 800; background: var(--accent-dim); color: var(--accent-lt); margin-left: 8px; border: 1px solid rgba(102,252,241,0.2); text-transform: uppercase; }
        .item-value { font-size: 18px; font-weight: 900; text-align: right; color: var(--accent-lt); letter-spacing: -0.5px; }
        
        .item-actions { display: flex; gap: 10px; margin-top: 12px; width: 100%; }
        .action-btn { flex: 1; background: var(--surface); color: var(--accent); border: 1px solid var(--border); padding: 10px; border-radius: 10px; font-size: 12px; font-weight: 800; cursor: pointer; text-decoration: none; display: flex; align-items: center; justify-content: center; gap: 6px; text-transform: uppercase; letter-spacing: 0.5px; }
        .action-btn.primary { background: var(--accent); color: #000; border: none; box-shadow: 0 4px 10px rgba(102,252,241,0.15); }

        /* Bottom Nav */
        nav {
            position: fixed; bottom: 0; left: 0; right: 0; height: var(--nav-h);
            background: rgba(18, 18, 18, 0.95); z-index: 50; display: flex; align-items: center; justify-content: space-around;
            padding: 0 8px calc(16px + env(safe-area-inset-bottom)) 8px; border-top: 1px solid var(--border); backdrop-filter: blur(15px);
        }
        .nav-item { flex: 1; display: flex; flex-direction: column; align-items: center; color: var(--text-3); text-decoration: none; font-size: 10px; font-weight: 800; padding-top: 12px; transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1); text-transform: uppercase; letter-spacing: 0.8px; }
        .nav-item.active { color: var(--accent); transform: translateY(-2px); }
        .nav-icon { margin-bottom: 5px; }

        /* Modal Sheets */
        .modal { display: none; position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.85); z-index: 1000; align-items: flex-end; backdrop-filter: blur(6px); }
        .modal-sheet { background: var(--surface); width: 100%; border-radius: 28px 28px 0 0; padding: 32px 24px calc(40px + env(safe-area-inset-bottom)) 24px; animation: slideUp 0.35s cubic-bezier(0.18, 0.89, 0.32, 1.1); border-top: 1px solid var(--border-lt); }
        @keyframes slideUp { from { transform: translateY(100%); } to { transform: translateY(0); } }
        .modal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 32px; }
        .modal-title { font-size: 22px; font-weight: 900; color: #fff; letter-spacing: -0.5px; }
        .modal-close { color: var(--text-3); cursor: pointer; padding: 4px; display: flex; }
        
        .input-group { margin-bottom: 24px; }
        .input-group label { display: block; margin-bottom: 10px; font-size: 12px; font-weight: 800; color: var(--text-2); text-transform: uppercase; letter-spacing: 0.8px; padding-left: 2px; }
        .input-group input, .input-group select { 
            width: 100%; padding: 18px; border-radius: 16px; border: 1px solid var(--border); background: var(--card); color: var(--text); font-size: 16px; transition: all 0.2s; -webkit-appearance: none;
        }
        .input-group input:focus { border-color: var(--accent); outline: none; background: #222; }
        .btn-large { width: 100%; padding: 20px; border-radius: 18px; border: none; background: var(--accent); color: #000; font-weight: 900; font-size: 17px; cursor: pointer; box-shadow: 0 8px 20px rgba(102,252,241,0.25); text-transform: uppercase; letter-spacing: 0.5px; }

        .tab-content { display: none; }
        .tab-content.active { display: block; animation: fadeIn 0.2s ease-out; }
        @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
        
        .empty-state { padding: 80px 20px; text-align: center; color: var(--text-3); font-size: 16px; font-weight: 600; display: flex; flex-direction: column; align-items: center; gap: 16px; }
        
        /* Logout in header */
        .logout-btn { font-size: 11px; font-weight: 800; color: #ff4444; text-decoration: none; background: rgba(239,68,68,0.12); padding: 8px 14px; border-radius: 10px; border: 1px solid rgba(239,68,68,0.2); text-transform: uppercase; letter-spacing: 0.8px; display: flex; align-items: center; gap: 6px; }
        
        /* Loading Overlay */
        #loading-overlay { display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.85); z-index: 2000; align-items: center; justify-content: center; flex-direction: column; backdrop-filter: blur(4px); }
        .spinner { width: 48px; height: 48px; border: 5px solid rgba(102,252,241,0.1); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s cubic-bezier(0.4, 0, 0.2, 1) infinite; margin-bottom: 20px; }
        @keyframes spin { to { transform: rotate(360deg); } }

        /* Period Selector */
        .period-select { 
            background: var(--surface); color: var(--accent); border: 1px solid var(--border); 
            padding: 8px 12px; border-radius: 10px; font-size: 13px; font-weight: 800; 
            outline: none; -webkit-appearance: none; cursor: pointer;
        }
    </style>
</head>
<body>

    <div id="loading-overlay">
        <div class="spinner"></div>
        <div style="color: var(--accent); font-weight: 800; text-transform: uppercase; letter-spacing: 1px;">Uploading...</div>
    </div>

    <header>
        <div>
            <h1 style="display:inline-block; vertical-align:middle; color:#fff;">Admin</h1><span style="color:var(--accent); font-weight:900; font-size:22px; margin-left:2px;">Panel</span>
            <div style="margin-top:2px;"><span class="user-tag">@<?= hsc($sys_username) ?></span></div>
        </div>
        <a href="?logout=1" class="logout-btn"><i data-lucide="log-out" style="width:14px; height:14px;"></i> Exit</a>
    </header>

    <!-- Hidden file input -->
    <input type="file" id="apk-file-input" style="display: none;" accept=".apk" onchange="handleFileSelect(event)">

    <!-- TAB: MAIN -->
    <div id="tab-main" class="tab-content <?= $active_tab === 'main' ? 'active' : '' ?>">
        <div class="section-title">Daily Overview</div>
        <div class="stats-grid">
            <?php foreach($stats_main as $lbl => $s): ?>
            <div class="stat-card">
                <i data-lucide="<?= $s['icon'] ?>"></i>
                <span class="val"><?= number_format($s['val']) ?></span>
                <span class="lbl"><?= $lbl ?></span>
            </div>
            <?php endforeach; ?>
        </div>

        <div class="section-title">Recent Actions</div>
        <div class="list-card">
            <?php foreach($recent_actions as $ra): ?>
            <div class="item">
                <div class="item-icon" style="border-radius: 50%; width: 40px; height: 40px;">
                    <i data-lucide="activity" style="width: 20px; height: 20px; color: var(--accent);"></i>
                </div>
                <div class="item-content">
                    <div class="item-title"><?= hsc(mapActionName($ra['log_type'])) ?></div>
                    <div class="item-sub"><?= hsc($ra['log_msg']) ?></div>
                </div>
                <div class="item-badge" style="background: transparent; border: none; color: var(--text-3); font-size: 10px; font-weight: 600;">
                    <?php 
                        $d = strtotime($ra['date'] . ' UTC');
                        echo date('H:i', $d);
                    ?>
                </div>
            </div>
            <?php endforeach; ?>
            <?php if(empty($recent_actions)): ?>
                <div class="empty-state">
                    <i data-lucide="activity" style="width:48px; height:48px;"></i>
                    No recent actions
                </div>
            <?php endif; ?>
        </div>
    </div>

    <!-- TAB: STATS -->
    <div id="tab-stats" class="tab-content <?= $active_tab === 'stats' ? 'active' : '' ?>">
        <div class="section-title">
            Overview
            <form id="period-form" method="GET" style="margin: 0;">
                <input type="hidden" name="tab" value="stats">
                <select name="period" class="period-select" onchange="document.getElementById('period-form').submit()">
                    <option value="today" <?= $period == 'today' ? 'selected' : '' ?>>Today</option>
                    <option value="yesterday" <?= $period == 'yesterday' ? 'selected' : '' ?>>Yesterday</option>
                    <option value="this_week" <?= $period == 'this_week' ? 'selected' : '' ?>>This Week</option>
                    <option value="this_month" <?= $period == 'this_month' ? 'selected' : '' ?>>This Month</option>
                    <option value="last_week" <?= $period == 'last_week' ? 'selected' : '' ?>>Last Week</option>
                    <option value="last_month" <?= $period == 'last_month' ? 'selected' : '' ?>>Last Month</option>
                    <option value="week" <?= $period == 'week' ? 'selected' : '' ?>>Last 7 Days</option>
                    <option value="month" <?= $period == 'month' ? 'selected' : '' ?>>Last 30 Days</option>
                    <option value="all_time" <?= $period == 'all_time' ? 'selected' : '' ?>>All Time</option>
                </select>
            </form>
        </div>
        <div class="stats-grid">
            <?php foreach($stats_period as $lbl => $s): ?>
            <div class="stat-card">
                <i data-lucide="<?= $s['icon'] ?>"></i>
                <span class="val"><?= number_format($s['val']) ?></span>
                <span class="lbl"><?= $lbl ?></span>
            </div>
            <?php endforeach; ?>
        </div>

        <?php if ($role === 'admin'): ?>
        <div class="section-title">Teams Performance</div>
        <?php foreach($team_stats as $ts): ?>
            <div class="list-card" style="margin-bottom: 16px;">
                <div style="padding: 12px 16px; font-weight: 800; font-size: 14px; background: rgba(255,255,255,0.02); border-bottom: 1px solid var(--border); color: var(--accent-lt);">
                    <i data-lucide="users" style="width: 14px; height: 14px; display: inline-block; vertical-align: middle; margin-right: 6px;"></i>
                    <?= hsc($ts['username']) ?>
                </div>
                <?php foreach($ts['apps'] as $a): ?>
                    <div class="item" style="flex-direction: column; align-items: flex-start;">
                        <div class="item-title" style="margin-bottom: 8px; color: #fff;"><i data-lucide="smartphone" style="width: 14px; height: 14px; display: inline-block; vertical-align: middle; margin-right: 4px; color: var(--text-3);"></i> <?= hsc($a['name']) ?></div>
                        <div style="display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 8px; width: 100%;">
                            <div style="background: var(--surface); border: 1px solid var(--border); padding: 8px; border-radius: 10px; text-align: center;">
                                <div style="font-size: 14px; font-weight: 900; color: var(--accent);"><?= number_format($a['opens']) ?></div>
                                <div style="font-size: 9px; color: var(--text-3); font-weight: 800; text-transform: uppercase;">Opens</div>
                            </div>
                            <div style="background: var(--surface); border: 1px solid var(--border); padding: 8px; border-radius: 10px; text-align: center;">
                                <div style="font-size: 14px; font-weight: 900; color: var(--accent);"><?= number_format($a['files']) ?></div>
                                <div style="font-size: 9px; color: var(--text-3); font-weight: 800; text-transform: uppercase;">Files</div>
                            </div>
                            <div style="background: var(--surface); border: 1px solid var(--border); padding: 8px; border-radius: 10px; text-align: center;">
                                <div style="font-size: 14px; font-weight: 900; color: var(--accent);"><?= number_format($a['locations']) ?></div>
                                <div style="font-size: 9px; color: var(--text-3); font-weight: 800; text-transform: uppercase;">Locations</div>
                            </div>
                            <div style="background: var(--surface); border: 1px solid var(--border); padding: 8px; border-radius: 10px; text-align: center;">
                                <div style="font-size: 14px; font-weight: 900; color: var(--accent);"><?= number_format($a['auths']) ?></div>
                                <div style="font-size: 9px; color: var(--text-3); font-weight: 800; text-transform: uppercase;">Auths</div>
                            </div>
                        </div>
                    </div>
                <?php endforeach; ?>
                <?php if(empty($ts['apps'])): ?>
                    <div style="padding: 16px; font-size: 12px; color: var(--text-3); text-align: center;">No apps associated</div>
                <?php endif; ?>
            </div>
        <?php endforeach; ?>
        <?php endif; ?>
    </div>

    <!-- TAB: APPS -->
    <div id="tab-apps" class="tab-content <?= $active_tab === 'apps' ? 'active' : '' ?>">
        <div class="section-title">List Apps</div>
        
        <div class="list-card">
            <?php foreach($apps as $a): ?>
            <div class="item" style="flex-direction: column; align-items: flex-start;">
                <div style="display: flex; align-items: center; width: 100%; cursor: pointer;" onclick="this.parentElement.querySelector('.app-builds').style.display = (this.parentElement.querySelector('.app-builds').style.display === 'block' ? 'none' : 'block')">
                    <div class="item-icon">
                        <?php if($a['icon']): ?>
                            <img src="/<?= hsc($a['icon']) ?>">
                        <?php else: ?>
                            <div class="placeholder"><?= substr($a['name'], 0, 1) ?></div>
                        <?php endif; ?>
                    </div>
                    <div class="item-content">
                        <div class="item-title"><?= hsc($a['name']) ?></div>
                    </div>
                    <i data-lucide="chevron-down"></i>
                </div>
                <div class="app-builds" style="display: none; width: 100%; margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border);">
                    <?php 
                        $app_builds = $pdo->prepare("SELECT b.*, su.username as owner_username FROM builds b LEFT JOIN system_users su ON b.owner_id = su.id WHERE b.app_id = ? ORDER BY b.created_at DESC");
                        $app_builds->execute([$a['id']]);
                        $builds_list = $app_builds->fetchAll(PDO::FETCH_ASSOC);
                    ?>
                    <?php foreach($builds_list as $b): ?>
                        <div style="display:flex; align-items:center; justify-content:space-between; padding: 12px 0; border-bottom: 1px solid var(--border-lt);">
                            <div>
                                <div style="font-size:13px; font-weight:800; color:#fff;">v<?= hsc($b['version']) ?> <span class="item-badge"><?= hsc($b['owner_username']) ?></span></div>
                            </div>
                            <div style="display: flex; gap: 8px;">
                                <?php if($b['apk_filename']): ?>
                                    <?php $apk_url = (strpos($b['apk_filename'], 'http') === 0) ? $b['apk_filename'] : '/' . $b['apk_filename']; ?>
                                    <a href="<?= hsc($apk_url) ?>" class="action-btn primary" download style="padding: 6px 10px; font-size: 11px;">
                                        <i data-lucide="download" style="width:12px; height:12px;"></i> Download
                                    </a>
                                <?php endif; ?>
                                <button class="action-btn" onclick="triggerUpload(<?= $b['id'] ?>)" style="padding: 6px 10px; font-size: 11px;">
                                    <i data-lucide="upload-cloud" style="width:12px; height:12px;"></i> <?= $b['apk_filename'] ? 'Replace' : 'Upload' ?>
                                </button>
                            </div>
                        </div>
                    <?php endforeach; ?>
                    <?php if(empty($builds_list)): ?>
                        <div style="padding: 8px 0; font-size:12px; color:var(--text-3); text-align: center;">No builds found</div>
                    <?php endif; ?>
                </div>
            </div>
            <?php endforeach; ?>
            <?php if(empty($apps)): ?><div class="empty-state">No apps found</div><?php endif; ?>
        </div>

        <div class="section-title">
            Recent Builds
            <?php if ($role === 'admin'): ?>
            <button class="add-btn" onclick="openModal('modal-build')"><i data-lucide="plus-circle" style="width:16px; height:16px;"></i> NEW</button>
            <?php endif; ?>
        </div>
        <div class="list-card">
            <?php foreach($builds as $b): ?>
            <div class="item" style="flex-direction: column; align-items: flex-start;">
                <div style="display: flex; align-items: center; width: 100%;">
                    <div class="item-icon">
                        <?php if($b['app_icon']): ?>
                            <img src="/<?= hsc($b['app_icon']) ?>">
                        <?php else: ?>
                            <div class="placeholder"><?= substr($b['original_app_name'], 0, 1) ?></div>
                        <?php endif; ?>
                    </div>
                    <div class="item-content">
                        <div class="item-title"><?= hsc($b['original_app_name']) ?></div>
                        <div class="item-sub">Version <?= hsc($b['version']) ?> • #<?= $b['id'] ?></div>
                    </div>
                    <div class="item-badge"><?= hsc($b['owner_username']) ?></div>
                </div>
                <div class="item-actions">
                    <?php if($b['apk_filename']): ?>
                        <?php 
                            $apk_url = (strpos($b['apk_filename'], 'http') === 0) ? $b['apk_filename'] : '/' . $b['apk_filename'];
                        ?>
                        <a href="<?= hsc($apk_url) ?>" class="action-btn primary" download>
                            <i data-lucide="download" style="width:14px; height:14px;"></i> Download
                        </a>
                    <?php endif; ?>
                    <button class="action-btn" onclick="triggerUpload(<?= $b['id'] ?>)">
                        <i data-lucide="upload-cloud" style="width:14px; height:14px;"></i> <?= $b['apk_filename'] ? 'Replace' : 'Upload' ?>
                    </button>
                </div>
            </div>
            <?php endforeach; ?>
            <?php if(empty($builds)): ?>
                <div class="empty-state">
                    <i data-lucide="package-search" style="width:48px; height:48px;"></i>
                    No active builds found
                </div>
            <?php endif; ?>
        </div>
    </div>

    <!-- Bottom Navigation -->
    <nav>
        <a href="?tab=main&period=<?= hsc($period) ?>" class="nav-item <?= $active_tab === 'main' ? 'active' : '' ?>">
            <i data-lucide="home" class="nav-icon"></i>
            <div>Main</div>
        </a>
        <a href="?tab=stats&period=<?= hsc($period) ?>" class="nav-item <?= $active_tab === 'stats' ? 'active' : '' ?>">
            <i data-lucide="pie-chart" class="nav-icon"></i>
            <div>Stats</div>
        </a>
        <a href="?tab=apps&period=<?= hsc($period) ?>" class="nav-item <?= $active_tab === 'apps' ? 'active' : '' ?>">
            <i data-lucide="layout-grid" class="nav-icon"></i>
            <div>Apps</div>
        </a>
    </nav>

    <!-- Modals -->
    <div id="modal-app" class="modal" onclick="if(event.target===this) closeModal('modal-app')">
        <div class="modal-sheet">
            <div class="modal-header">
                <div class="modal-title">New Application</div>
                <div class="modal-close" onclick="closeModal('modal-app')"><i data-lucide="x"></i></div>
            </div>
            <form method="POST">
                <input type="hidden" name="action" value="add_app">
                <div class="input-group">
                    <label>App Name</label>
                    <input type="text" name="name" required placeholder="Shadow VPN Pro">
                </div>
                <button type="submit" class="btn-large">Create Application</button>
            </form>
        </div>
    </div>

    <div id="modal-build" class="modal" onclick="if(event.target===this) closeModal('modal-build')">
        <div class="modal-sheet">
            <div id="build-step1">
                <div class="modal-header">
                    <div class="modal-title">New Build (1/2)</div>
                    <div class="modal-close" onclick="closeModal('modal-build')"><i data-lucide="x"></i></div>
                </div>
                <div class="input-group">
                    <label>Partner / Owner</label>
                    <select id="b-owner-id" required>
                        <?php foreach($system_users as $su): ?>
                        <option value="<?= $su['id'] ?>"><?= hsc($su['username']) ?></option>
                        <?php endforeach; ?>
                    </select>
                </div>
                <div class="input-group">
                    <label>Target App</label>
                    <select id="b-app-id" required>
                        <?php foreach($apps as $a): ?>
                        <option value="<?= $a['id'] ?>"><?= hsc($a['name']) ?></option>
                        <?php endforeach; ?>
                    </select>
                </div>
                <div class="input-group">
                    <label>Version</label>
                    <input type="text" id="b-version" required placeholder="e.g. 1.0.1">
                </div>
                <div class="input-group">
                    <label>Notes</label>
                    <input type="text" id="b-notes" placeholder="Optional notes">
                </div>
                <button class="btn-large" onclick="createBuildStep1()">Continue</button>
            </div>
            
            <div id="build-step2" style="display:none;">
                <div class="modal-header">
                    <div class="modal-title">Upload APK (2/2)</div>
                    <div class="modal-close" onclick="closeModal('modal-build')"><i data-lucide="x"></i></div>
                </div>
                <div style="text-align:center; padding: 20px 0; color: var(--text-2);">
                    Build #<span id="b-created-id" style="color:var(--accent);">...</span> created.<br>Please select APK file now.
                </div>
                <button class="btn-large" onclick="document.getElementById('apk-file-input').click()">Select APK File</button>
                <div style="margin-top:12px; text-align:center; font-size:12px; color:var(--text-3);">You can upload later from dashboard</div>
            </div>
        </div>
    </div>

    <script>
        const tg = window.Telegram.WebApp;
        tg.expand();
        tg.headerColor = '#121212';
        tg.backgroundColor = '#0a0a0a';

        lucide.createIcons();

        let currentUploadBuildId = null;

        function openModal(id) {
            if (tg.HapticFeedback) tg.HapticFeedback.impactOccurred('medium');
            document.getElementById(id).style.display = 'flex';
        }
        
        function closeModal(id) {
            document.getElementById(id).style.display = 'none';
        }

        function triggerUpload(buildId) {
            currentUploadBuildId = buildId;
            document.getElementById('apk-file-input').click();
        }

        async function handleFileSelect(event) {
            const file = event.target.files[0];
            if (!file || !currentUploadBuildId) return;

            if (!file.name.endsWith('.apk')) {
                tg.showAlert('Please select a valid .apk file');
                return;
            }

            const formData = new FormData();
            formData.append('id', currentUploadBuildId);
            formData.append('apk', file);

            document.getElementById('loading-overlay').style.display = 'flex';

            try {
                const response = await fetch('/api/builds/upload_apk.php', {
                    method: 'POST',
                    body: formData
                });
                const result = await response.json();

                if (result.success) {
                    if (tg.HapticFeedback) tg.HapticFeedback.notificationOccurred('success');
                    setTimeout(() => location.reload(), 500);
                } else {
                    tg.showAlert('Upload failed: ' + (result.message || 'Unknown error'));
                }
            } catch (error) {
                tg.showAlert('Network error occurred during upload');
            } finally {
                document.getElementById('loading-overlay').style.display = 'none';
            }
        }

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                document.querySelectorAll('.modal').forEach(m => m.style.display = 'none');
            }
        });
        
        // This function handles the build creation step
        async function createBuildStep1() {
            const ownerId = document.getElementById('b-owner-id').value;
            const appId = document.getElementById('b-app-id').value;
            const version = document.getElementById('b-version').value;
            const notes = document.getElementById('b-notes').value;
            
            if (!version) {
                tg.showAlert('Version is required');
                return;
            }

            // Simple POST form equivalent using fetch
            const formData = new FormData();
            formData.append('action', 'add_build');
            formData.append('owner_id', ownerId);
            formData.append('app_id', appId);
            formData.append('version', version);
            formData.append('notes', notes);
            
            try {
                document.getElementById('loading-overlay').style.display = 'flex';
                // Wait for the form POST to process and reload page
                await fetch(window.location.href, { method: 'POST', body: formData });
                // Note: Normally the server redirects to GET. 
                // A better approach is full page reload.
                window.location.href = '?tab=apps';
            } catch(e) {
                tg.showAlert('Error creating build');
                document.getElementById('loading-overlay').style.display = 'none';
            }
        }
    </script>
</body>
</html>
