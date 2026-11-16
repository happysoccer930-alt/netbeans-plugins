const vscode = require('vscode');
const net = require('net');
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const HOST = '127.0.0.1';
const PORT = 1235;
const BIONIC = path.join(process.env.LOCALAPPDATA || '', 'Programs', 'Bionic', 'Bionic.exe');

let item;

function probe() {
    return new Promise((resolve) => {
        const socket = net.connect({ host: HOST, port: PORT });
        socket.setTimeout(800);
        const done = (value) => {
            try { socket.destroy(); } catch (e) { /* ignore */ }
            resolve(value);
        };
        socket.once('connect', () => done(true));
        socket.once('timeout', () => done(false));
        socket.once('error', () => done(false));
    });
}

function autocompleteOn() {
    return vscode.workspace.getConfiguration('continue').get('enableTabAutocomplete') !== false;
}

async function refresh() {
    if (!item) {
        return;
    }
    const up = await probe();
    const on = autocompleteOn();
    item.text = `${up ? '$(zap)' : '$(debug-disconnect)'} IA local ${on ? 'ON' : 'OFF'}`;
    item.tooltip = [
        `Bionic: ${up ? 'activo' : 'apagado'} en ${HOST}:${PORT}`,
        `Autocompletado: ${on ? 'activado' : 'desactivado'}`,
        '',
        'Clic izquierdo: activar / desactivar autocompletado',
        'Clic derecho: abrir Bionic',
    ].join('\n');
    item.color = up ? undefined : new vscode.ThemeColor('statusBarItem.warningForeground');
    item.backgroundColor = up ? undefined : new vscode.ThemeColor('statusBarItem.warningBackground');
    item.show();
}

async function toggle() {
    const up = await probe();
    if (!up) {
        await startServer();
        if (await probe()) {
            await vscode.workspace.getConfiguration('continue')
                .update('enableTabAutocomplete', true, vscode.ConfigurationTarget.Global);
            refresh();
        }
        return;
    }
    const next = !autocompleteOn();
    await vscode.workspace.getConfiguration('continue').update('enableTabAutocomplete', next, vscode.ConfigurationTarget.Global);
    vscode.window.setStatusBarMessage(
        `IA local: autocompletado ${next ? 'activado' : 'desactivado'}`, 3000);
    refresh();
}

function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

async function startServer() {
    if (await probe()) {
        vscode.window.showInformationMessage('La IA local ya esta activa en 127.0.0.1:1235.');
        refresh();
        return;
    }
    if (!fs.existsSync(BIONIC)) {
        vscode.window.showErrorMessage('No encontre Bionic en: ' + BIONIC);
        return;
    }
    spawn(BIONIC, [], { detached: true, stdio: 'ignore' }).unref();
    vscode.window.setStatusBarMessage('Abriendo Bionic...', 5000);
    for (let i = 0; i < 40; i++) {
        await sleep(1500);
        if (await probe()) {
            vscode.window.showInformationMessage('IA local activa en 127.0.0.1:1235 (qwen2.5-coder-1.5b).');
            refresh();
            return;
        }
    }
    vscode.window.showWarningMessage('Bionic no respondio en el puerto 1235. Abrelo manualmente.');
    refresh();
}

function activate(context) {
    item = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
    item.command = 'iaLocal.toggle';
    context.subscriptions.push(
        item,
        vscode.commands.registerCommand('iaLocal.toggle', toggle),
        vscode.commands.registerCommand('iaLocal.start', startServer),
        vscode.commands.registerCommand('iaLocal.refresh', refresh)
    );
    refresh();
    const timer = setInterval(refresh, 15000);
    context.subscriptions.push({ dispose: () => clearInterval(timer) });
}

function deactivate() {
    if (item) {
        item.dispose();
        item = undefined;
    }
}

module.exports = { activate, deactivate };
