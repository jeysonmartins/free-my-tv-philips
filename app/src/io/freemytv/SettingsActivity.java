        private boolean scrolling() {
            return screen == PICKER || screen == SHELF_EDIT || screen == HOME_SETTINGS || screen == PICK_FROM || screen == WATCH_APPS;
        }

        private int listSize() {
            if (screen == WATCH_APPS) return watchApps == null ? 0 : watchApps.size() + (watchHiddenCount > 0 ? 1 : 0);
            return screen == SHELF_EDIT ? shelfRows.size() : screen == HOME_SETTINGS ? settingLabels().length : screen == PICK_FROM ? services.size() : pickLabels.size();
        }

        private static final long WATCH_GRANT_MS = 10_000;

        private final Runnable askWatch = this::askWatch;
        private boolean watchPickPending, watchGrantPending;

        private void commitWatch() {
            if (!watchPickPending) return;
            watchPickPending = false;
            handler.removeCallbacks(askWatch);
            if (Prefs.topContent().equals(Prefs.TOP_CONTINUE) && !WatchNext.allowed(getContext())) {
                HomeMode.requestWatchGrant(getContext());
                watchGrantPending = true;
                handler.postDelayed(askWatch, WATCH_GRANT_MS);
            }
        }

        private void askWatch() {
            if (!running) return;
            watchGrantPending = false;
            if (Prefs.topContent().equals(Prefs.TOP_CONTINUE) && !WatchNext.allowed(getContext())) {
                activity.requestPermissions(new String[] {WatchNext.PERMISSION}, 1);
            }
        }

        private boolean watchLocked() {
            return !Prefs.topContent().equals(Prefs.TOP_CONTINUE);
        }

        private void scanWatchApps() {
            final int scan = ++watchScan;
            final Context c = getContext().getApplicationContext();
            new Thread(() -> {
                final List<String[]> found = WatchNext.sources(c);
                handler.post(() -> {
                    if (scan != watchScan) return;
                    watchApps = found;
                    if (screen == WATCH_APPS) {
                        pickScroll = 0;
                        focus = 0;
                        spoken.focusMoved();
                    }
                    invalidate();
                });
            }, "watch-apps").start();
        }

        private String watchCount() {
            if (watchApps == null) return "…";
            Set<String> off = Prefs.watchOff();
            int on = 0;
            for (String[] app : watchApps) if (!off.contains(app[0])) on++;
            return Ui.trf("{on} of {total}", "on", on, "total", watchApps.size());
        }

        private String hiddenLabel() {
            return Ui.trf("Show hidden items ({n})", "n", watchHiddenCount);
        }

        private void openWatchApps() {
            watchHiddenCount = Prefs.watchHidden().size();
            pickScroll = 0;
            focus = 0;
            screen = WATCH_APPS;
        }

        private void toggleWatchApp() {
            int at = pickScroll + focus;
            if (watchApps == null || at >= listSize()) return;
            if (at == watchApps.size()) {
                Prefs.setWatchHidden(new HashSet<>());
                watchHiddenCount = 0;
                int size = listSize();
                if (pickScroll > 0 && pickScroll + PICK_VISIBLE > size) pickScroll = Math.max(0, size - PICK_VISIBLE);
                focus = Math.max(0, Math.min(size - pickScroll, PICK_VISIBLE) - 1);
                announceForAccessibility(Ui.tr("Hidden items are back"));
                spoken.focusMoved();
                return;
            }
            Set<String> off = Prefs.watchOff();
            String pkg = watchApps.get(at)[0];
            if (!off.remove(pkg)) off.add(pkg);
            Prefs.setWatchOff(off);
            spoken.focusMoved();
        }

        private void drawWatchApps(Canvas c) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            c.drawText(Ui.tr("Continue Watching apps"),
                    LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            Paint hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            if (watchApps == null || watchApps.isEmpty() && watchHiddenCount == 0) {
                c.drawText(watchApps == null ? Ui.tr("Looking for the apps…")
                        : Ui.tr("No app on this TV shares what you watch."), LIST_X * k + 30 * k, (LIST_TOP + 56) * k, hint);
                return;
            }
            Set<String> off = Prefs.watchOff();
            int rows = rowCount();
            for (int i = 0; i < rows; i++) {
                int at = pickScroll + i;
                boolean hiddenRow = at == watchApps.size();
                boolean on = hiddenRow || !off.contains(watchApps.get(at)[0]);
                float y = (LIST_TOP + 20 + i * (LIST_ROW_H + LIST_ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + LIST_ROW_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(on ? 0xF2FFFFFF : 0x80FFFFFF, on ? INK : 0x801C1C1E, f));
                rowValue.setColor(blend(0xB3FFFFFF, 0xB31C1C1E, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                String label = hiddenRow ? hiddenLabel() : watchApps.get(at)[1];
                c.drawText(TextUtils.ellipsize(label, new TextPaint(rowLabel), 560 * k, TextUtils.TruncateAt.END).toString(), rowRect.left + 30 * k, base, rowLabel);
                if (!hiddenRow) c.drawText(onOff(on), rowRect.right - 30 * k, base, rowValue);
            }
            float hy = (LIST_TOP + 20 + rows * (LIST_ROW_H + LIST_ROW_GAP) + 36) * k;
            c.drawText(Ui.tr("OK turns an app on or off in Continue Watching."), LIST_X * k + 30 * k, hy, hint);
        }

        private void openShelfEditor(boolean top) {
            editingTop = top;
            editingInputs = false;
            shelfOrder.clear();
            if (top) {
                shelfOrder.addAll(HomeApps.topButtonKeys());
                rebuildShelfRows(HomeApps.topCandidates());
            } else {
                PackageManager pm = getContext().getPackageManager();
                for (String pkg : HomeApps.shelfPackages(getContext())) if (HomeApps.launchIntent(pm, pkg) != null) shelfOrder.add(pkg);
                rebuildShelfRows(HomeApps.shelfCandidates(getContext()));
            }
            pickScroll = 0;
            focus = 0;
            screen = SHELF_EDIT;
        }

        private void openInputsEditor() {
            editingTop = false;
            editingInputs = true;
            shelfOrder.clear();
            for (HomeApps.Item it : HomeApps.orderedInputs(getContext())) shelfOrder.add(it.key);
            rebuildShelfRows(HomeApps.inputCandidates(getContext()));
            pickScroll = 0;
            focus = 0;
            screen = SHELF_EDIT;
        }

        private void rebuildShelfRows(List<String[]> candidates) {
            shelfRows.clear();
            if (!editingTop && !editingInputs) shelfRows.add(new String[] {ORDER_ROW, Ui.tr("Shelf order")});
            for (String pkg : shelfOrder) {
                for (String[] cand : candidates) if (cand[0].equals(pkg)) { shelfRows.add(cand); break; }
            }
            for (String[] cand : candidates) {
                if (!shelfOrder.contains(cand[0]) && !cand[0].equals(ORDER_ROW)) shelfRows.add(cand);
            }
        }

        private boolean shelfOrderLocked() {
            return !editingTop && !editingInputs && Prefs.shelfRecent();
        }

        private void switchShelfOrder() {
            boolean on = !Prefs.shelfRecent();
            Prefs.setShelfRecent(on);
            if (!on) Recent.clear(getContext());
        }

        private String shelfOrderValue() {
            return Prefs.shelfRecent() ? Ui.tr("Last opened first") : Ui.tr("My order");
        }

        private void refreshShelfRows(String focusedPkg) {
            List<String[]> all = new ArrayList<>(shelfRows);
            rebuildShelfRows(all);
            for (int i = 0; i < shelfRows.size(); i++) {
                if (!shelfRows.get(i)[0].equals(focusedPkg)) continue;
                int visible = rowCount();
                if (i < pickScroll) pickScroll = i;
                else if (i >= pickScroll + visible) pickScroll = i - visible + 1;
                focus = i - pickScroll;
                break;
            }
            if (editingTop) Prefs.setTopButtons(shelfOrder);
            else if (editingInputs) saveInputsOrder();
            else Prefs.setShelf(shelfOrder);
        }

        private void saveInputsOrder() {
            List<String> saved = Prefs.inputsOrder();
            Prefs.setInputsOrder(VisibleOrder.replace(saved == null ? shelfOrder : saved, shelfOrder));
            List<String> hidden = new ArrayList<>(Prefs.inputsHidden());
            hidden.removeAll(shelfOrder);
            for (String[] row : shelfRows) {
                if (!shelfOrder.contains(row[0]) && !hidden.contains(row[0])) hidden.add(row[0]);
            }
            Prefs.setInputsHidden(hidden);
        }

        private void toggleShelfApp() {
            if (pickScroll + focus >= shelfRows.size()) return;   
            String pkg = shelfRows.get(pickScroll + focus)[0];
            if (pkg.equals(ORDER_ROW)) {
                switchShelfOrder();
                return;
            }
            if (shelfOrder.contains(pkg)) shelfOrder.remove(pkg);
            else if (editingTop || editingInputs || shelfOrder.size() < HomeApps.maxShelf(getContext())) shelfOrder.add(pkg);
            else return;
            refreshShelfRows(pkg);
        }

        private void moveShelfApp(int dir) {
            if (pickScroll + focus >= shelfRows.size()) return;
            String pkg = shelfRows.get(pickScroll + focus)[0];
            if (pkg.equals(ORDER_ROW)) return;
            int at = shelfOrder.indexOf(pkg);
            if (at < 0) return;
            int next = at + dir;
            if (next < 0 || next >= shelfOrder.size()) return;
            shelfOrder.set(at, shelfOrder.get(next));
            shelfOrder.set(next, pkg);
            refreshShelfRows(pkg);
        }

        private void drawShelfEditor(Canvas c) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            c.drawText(editingTop ? Ui.tr("Top Buttons") : editingInputs ? Ui.tr("Inputs") : Ui.tr("Shelf"),
                    LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            Paint hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            int rows = rowCount();
            for (int i = 0; i < rows; i++) {
                int at = pickScroll + i;
                if (at >= shelfRows.size()) break;
                String[] app = shelfRows.get(at);
                boolean selected = app[0].equals(ORDER_ROW) || shelfOrder.contains(app[0]);
                float y = (LIST_TOP + 20 + i * (LIST_ROW_H + LIST_ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + LIST_ROW_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(selected ? 0xF2FFFFFF : 0x80FFFFFF, selected ? INK : 0x801C1C1E, f));
                rowValue.setColor(blend(0xB3FFFFFF, 0xB31C1C1E, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                c.drawText(app[1], rowRect.left + 30 * k, base, rowLabel);

                if (app[0].equals(ORDER_ROW)) {
                    c.drawText(shelfOrderValue(), rowRect.right - 30 * k, base, rowValue);
                } else {
                    int place = shelfOrder.indexOf(app[0]);
                    if (place >= 0) {
                        c.drawText(String.valueOf(place + 1), rowRect.right - 30 * k, base, rowValue);
                    }
                }
            }
            float hy = (LIST_TOP + 20 + rows * (LIST_ROW_H + LIST_ROW_GAP) + 36) * k;
            c.drawText(Ui.tr("OK adds or removes. ← → moves."), LIST_X * k + 30 * k, hy, hint);
        }

        private void drawServices(Canvas c) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            c.drawText(Ui.tr("Picks from"), LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            Paint hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            if (services.isEmpty()) {
                c.drawText(Ui.tr("No streaming service found."), LIST_X * k + 30 * k, (LIST_TOP + 56) * k, hint);
                return;
            }
            int rows = rowCount();
            for (int i = 0; i < rows; i++) {
                int at = pickScroll + i;
                if (at >= services.size()) break;
                boolean on = !Prefs.serviceOff(services.get(at)[0]);
                float y = (LIST_TOP + 20 + i * (LIST_ROW_H + LIST_ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + LIST_ROW_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(on ? 0xF2FFFFFF : 0x80FFFFFF, on ? INK : 0x801C1C1E, f));
                rowValue.setColor(blend(0xB3FFFFFF, 0xB31C1C1E, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                c.drawText(services.get(at)[1], rowRect.left + 30 * k, base, rowLabel);
                c.drawText(onOff(on), rowRect.right - 30 * k, base, rowValue);
            }
            float hy = (LIST_TOP + 20 + rows * (LIST_ROW_H + LIST_ROW_GAP) + 36) * k;
            c.drawText(Ui.tr("OK turns a service on or off in the picks."), LIST_X * k + 30 * k, hy, hint);
        }

        private void countServicesOff() {
            someServicesOff = false;
            for (String[] s : services) if (Prefs.serviceOff(s[0])) someServicesOff = true;
        }

        private void toggleService() {
            int at = pickScroll + focus;
            if (at >= services.size()) return;
            String key = services.get(at)[0];
            Prefs.setServiceOff(key, !Prefs.serviceOff(key));
            countServicesOff();
            spoken.focusMoved();
        }

        private void drawPicker(Canvas c) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            String title = "";
            if (pickFor == PICK_LANGUAGE) title = Ui.tr("Language");
            else if (pickFor == PICK_CITY) title = Ui.tr("City");
            else if (pickFor == PICK_SLEEP) title = Ui.tr("Sleep Timer");
            else if (pickFor == PICK_PLACE) title = Ui.tr("Position");
            else if (pickFor == PICK_PHOTOS) title = Ui.tr("My Photos");
            else if (pickFor == PICK_ALBUM) title = Ui.tr("Immich albums");
            else if (pickFor > 0) title = Ui.tr("App for the remote button");
            c.drawText(title, LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);

            int rows = rowCount();
            for (int i = 0; i < rows; i++) {
                int at = pickScroll + i;
                if (at >= pickLabels.size()) break;
                boolean selected = at == pickCurrent;
                float y = (LIST_TOP + 20 + i * (LIST_ROW_H + LIST_ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + LIST_ROW_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                c.drawText(pickLabels.get(at), rowRect.left + 30 * k, base, rowLabel);
                if (selected) {
                    float cw = Symbols.width(getContext(), "check", 26 * k);
                    Symbols.draw(getContext(), c, "check", rowRect.right - 30 * k - cw / 2, rowRect.centerY(), 26 * k, blend(0xFFFFFFFF, INK, f));
                }
            }
        }

        private void openPicker(int forWhat) {
            pickFor = forWhat;
            pickLabels.clear();
            pickCurrent = -1;
            if (forWhat == PICK_LANGUAGE) {
                String[] v = languageChoices();
                for (int i = 0; i < v.length; i++) {
                    pickLabels.add(i == 0 ? Ui.tr("Automatic") : Ui.languageName(v[i]));
                    if (v[i].equals(Prefs.language())) pickCurrent = i;
                }
            } else if (forWhat == PICK_CITY) {
                for (int i = 0; i < cities.size(); i++) {
                    pickLabels.add(cities.get(i)[1]);
                    if (cities.get(i)[0].equals(Prefs.city())) pickCurrent = i;
                }
            } else if (forWhat <= PICK_SLEEP) {
                for (int i = 0; i < choices.size(); i++) {
                    pickLabels.add(choices.get(i)[1]);
                    if (choices.get(i)[0].equals(choiceNow)) pickCurrent = i;
                }
            } else if (forWhat == PICK_PHOTOS || forWhat == PICK_ALBUM) {
                for (int i = 0; i < choices.size(); i++) {
                    pickLabels.add(choices.get(i)[1]);
                    if (choices.get(i)[0].equals(choiceNow)) pickCurrent = i;
                }
            } else {
                loadApps();
                for (int i = 0; i < appLabels.size(); i++) {
                    pickLabels.add(appLabels.get(i));
                    if (appPkgs.get(i).equals(Prefs.keyApp(Prefs.KEYS[forWhat - 1]))) pickCurrent = i;
                }
            }
            pickScroll = Math.max(0, pickCurrent - (PICK_VISIBLE / 2));
            if (pickScroll + PICK_VISIBLE > pickLabels.size()) pickScroll = Math.max(0, pickLabels.size() - PICK_VISIBLE);
            focus = pickCurrent >= 0 ? pickCurrent - pickScroll : 0;
            screen = PICKER;
        }

        private void pick() {
            int at = pickScroll + focus;
            if (at >= pickLabels.size()) return;
            if (pickFor == PICK_LANGUAGE) {
                Prefs.setLanguage(languageChoices()[at]);
                showSettingsRow(0);
            } else if (pickFor == PICK_CITY) {
                Prefs.setCity(cities.get(at)[0], cities.get(at)[1]);
                showSettingsRow(CITY_ROW);
            } else if (pickFor == PICK_SLEEP) {
                pickPower();
            } else if (pickFor == PICK_PLACE) {
                pickPower();
            } else if (pickFor == PICK_PHOTOS || pickFor == PICK_ALBUM) {
                pickPhotoSource();
            } else {
                Prefs.setKeyApp(Prefs.KEYS[pickFor - 1], appPkgs.get(at));
                HomeMode.ensureRemoteService(getContext(), 3_000);
                screen = REMOTE_KEYS;
                focus = pickFor - 1;
            }
            whyRows = null;
            invalidate();
            spoken.focusMoved();
        }

        private void drawList(Canvas c) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            c.drawText(Ui.tr("Home Settings"), LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            String[] labels = listLabels();
            String[] values = listValues();
            int rows = rowCount();
            for (int i = 0; i < rows; i++) {
                int at = pickScroll + i;
                if (at >= labels.length) break;
                float y = (LIST_TOP + 20 + i * (LIST_ROW_H + LIST_ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + LIST_ROW_H * k);
                float f = drawRowBackground(c, i);
                boolean off = unavailable(i) != null;
                rowLabel.setColor(off ? blend(0x73FFFFFF, 0x731C1C1E, f) : blend(0xF2FFFFFF, INK, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                c.drawText(labels[at], rowRect.left + 30 * k, base, rowLabel);
                float right = rowRect.right - 30 * k;
                if (values[at].equals("›")) {
                    float cw = Symbols.width(getContext(), "chevron_right", 22 * k);
                    Symbols.draw(getContext(), c, "chevron_right", right - cw / 2, rowRect.centerY(), 22 * k, blend(0x66FFFFFF, 0x991C1C1E, f));
                } else {
                    rowValue.setColor(off ? blend(0x59FFFFFF, 0x591C1C1E, f) : blend(0x8CFFFFFF, 0x8C1C1C1E, f));
                    String shown = TextUtils.ellipsize(values[at], new TextPaint(rowValue), 470 * k, TextUtils.TruncateAt.END).toString();
                    c.drawText(shown, right, base, rowValue);
                }
            }
            if (screen == HOME_SETTINGS) {
                Paint hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
                float hy = (LIST_TOP + 20 + rows * (LIST_ROW_H + LIST_ROW_GAP) + 36) * k;
                c.drawText(Ui.tr("OK changes the focused setting; ← → step through the values."), LIST_X * k + 30 * k, hy, hint);
            }
        }

        private void activate() {
            if (tellWhy()) return;

            if (screen == HOME_SETTINGS && pickScroll + focus == fastVolumeRow()) {
                adjust(1);
                return;
            }

            if (screen == MAIN) {
                if (focus == 0) screen = HOME_CHOICE;
                else if (focus == 1) activity.showPreview();
                else if (focus == 2) {
                    screen = HOME_SETTINGS;
                    focus = 0;
                    pickScroll = 0;
                } else if (focus == 3) openShelfEditor(false);
                else if (focus == 4) openShelfEditor(true);
                else if (focus == 5) openInputsEditor();
                else if (focus == 6) {
                    screen = REMOTE_KEYS;
                    focus = 0;
                    pickScroll = 0;
                } else if (focus == 7) {
                    screen = POWER;
                    focus = 0;
                    pickScroll = 0;
                } else if (focus == 8) {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                }
            } else if (screen == HOME_CHOICE) {
                if (focus == 0 && !freeActive()) {
                    pendingAt = SystemClock.uptimeMillis();
                    requestedAt = System.currentTimeMillis();
                    ModeJob.schedule(getContext(), ModeJob.SWITCH, HomeMode.FREE, 0);
                    spoken.focusMoved();
                } else if (focus == 1 && freeActive()) {
                    pendingAt = SystemClock.uptimeMillis();
                    requestedAt = System.currentTimeMillis();
                    ModeJob.schedule(getContext(), ModeJob.SWITCH, HomeMode.ORIGINAL, 0);
                    spoken.focusMoved();
                }
            } else if (screen == HOME_SETTINGS) {
                int row = pickScroll + focus;
                if (row == 0) openPicker(PICK_LANGUAGE);
                else if (row == CITY_ROW) askCity();
                else if (row == PICKS_ROW) { screen = PICKS; focus = 0; pickScroll = 0; }
                else if (row == WATCH_ROW) openWatchApps();
                else if (row == keyRow()) openKeyScreen(false);
                else if (row == watchmodeRow()) openKeyScreen(true);
                else if (row == updatesRow()) updateNow();
                else if (row == PHOTOS_ROW) {
                    if (ImmichPhotos.configured(getContext())) openPhotoSources();
                    else askRemovePhotos();
                } else {
                    adjust(1);
                }
            } else if (screen == REMOTE_KEYS) {
                openPicker(focus + 1);
            } else if (screen == PICKER) {
                pick();
            } else if (screen == SHELF_EDIT) {
                toggleShelfApp();
            } else if (screen == PICKS) {
                if (focus == 1) {
                    screen = PICK_FROM;
                    focus = 0;
                    pickScroll = 0;
                } else if (traktRow() >= 0 && focus == traktRow()) {
                    traktOk();
                } else {
                    adjust(1);
                }
            } else if (screen == PICK_FROM) {
                toggleService();
            } else if (screen == WATCH_APPS) {
                toggleWatchApp();
            } else if (screen == POWER) {
                activatePower();
            } else if (screen == TMDB_KEY) {
                activateKey();
            }
            invalidate();
            spoken.focusMoved();
        }
    }
}
